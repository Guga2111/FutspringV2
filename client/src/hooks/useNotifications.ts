import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { useNavigate } from "react-router-dom"
import { toast } from "sonner"
import {
  NOTIFICATIONS_PAGE_SIZE,
  getNotifications,
  getUnreadNotificationCount,
  markAllNotificationsRead,
  markNotificationRead,
} from "@/api/notifications"
import type { NotificationFilter, NotificationsContextValue } from "@/context/notifications-context-value"
import { useRealtime } from "@/hooks/useRealtime"
import type { NotificationPush, NotificationResponse } from "@/types/notification"
import { getErrorMessage } from "@/lib/errors"
import {
  mergeNotifications,
  notificationPlainText,
  notificationToast,
  notificationView,
  upsertNotification,
} from "@/utils/notifications"

interface ListState {
  filter: NotificationFilter
  items: NotificationResponse[]
  page: number
  hasMore: boolean
  status: "idle" | "loading" | "ready" | "error"
  loadingMore: boolean
}

const initialList: ListState = { filter: "all", items: [], page: 0, hasMore: false, status: "idle", loadingMore: false }

// The bell's state: the unread counter and the list, loaded when the panel opens. New notifications arrive on
// /user/queue/notifications with the server's unread count; the counter is also fetched on start, on every
// (re)connect (catches up on what was missed while offline) and whenever the tab becomes visible again; no polling.
// Called once by AppLayout, inside RealtimeProvider, and shared through NotificationsContext.
// `onBumpsRead` mirrors the server, where reading a "perdeu a vaga" notification also dismisses the bump alert
// (null = every session, after "Marcar todas como lidas").
export function useNotifications(onBumpsRead: (dailyId: number | null) => void): NotificationsContextValue {
  const [unreadCount, setUnreadCount] = useState<number | null>(null)
  const [list, setList] = useState<ListState>(initialList)
  // only the latest list request may write; a filter change or a refresh drops the older answers
  const requestRef = useRef(0)
  const { subscribe, connectionId } = useRealtime()
  const navigate = useNavigate()

  const fetchCount = useCallback(() => {
    getUnreadNotificationCount()
      .then(setUnreadCount)
      .catch(() => {
        // the badge keeps its last value; the next visibility change tries again
      })
  }, [])

  useEffect(() => {
    fetchCount()
    function onVisibilityChange() {
      if (document.visibilityState === "visible") fetchCount()
    }
    document.addEventListener("visibilitychange", onVisibilityChange)
    return () => document.removeEventListener("visibilitychange", onVisibilityChange)
  }, [fetchCount])

  useEffect(() => {
    if (connectionId > 0) fetchCount()
  }, [connectionId, fetchCount])

  const load = useCallback(async (filter: NotificationFilter, page: number) => {
    const request = ++requestRef.current
    setList((prev) => {
      if (page > 0) return { ...prev, loadingMore: true }
      // refreshing the same filter keeps the current items on screen
      const keep = prev.filter === filter && prev.status === "ready"
      return keep ? prev : { ...initialList, filter, status: "loading" }
    })
    try {
      const data = await getNotifications({ page, unread: filter === "unread" })
      if (request !== requestRef.current) return
      setList((prev) => ({
        filter,
        items: page === 0 ? data : mergeNotifications(prev.items, data),
        page,
        hasMore: data.length === NOTIFICATIONS_PAGE_SIZE,
        status: "ready",
        loadingMore: false,
      }))
    } catch (error) {
      if (request !== requestRef.current) return
      if (page === 0) {
        setList((prev) => (prev.status === "ready" ? prev : { ...prev, status: "error" }))
      } else {
        setList((prev) => ({ ...prev, loadingMore: false }))
        toast.error(getErrorMessage(error, "Não foi possível carregar mais notificações"))
      }
    }
  }, [])

  const refresh = useCallback(() => {
    fetchCount()
    void load(list.filter, 0)
  }, [fetchCount, load, list.filter])

  const setFilter = useCallback((filter: NotificationFilter) => void load(filter, 0), [load])

  const loadMore = useCallback(() => {
    if (list.loadingMore || !list.hasMore) return
    void load(list.filter, list.page + 1)
  }, [load, list.filter, list.page, list.hasMore, list.loadingMore])

  const setReadAt = useCallback((match: (n: NotificationResponse) => boolean, readAt: string | null) => {
    setList((prev) => ({ ...prev, items: prev.items.map((n) => (match(n) ? { ...n, readAt } : n)) }))
  }, [])

  const markRead = useCallback(
    (notification: NotificationResponse) => {
      if (notification.readAt) return
      setReadAt((n) => n.id === notification.id, new Date().toISOString())
      setUnreadCount((c) => (c === null ? c : Math.max(0, c - 1)))
      markNotificationRead(notification.id)
        .then(() => {
          if (notification.type === "DIARISTA_BUMPED" && notification.daily) onBumpsRead(notification.daily.id)
        })
        .catch((error) => {
          setReadAt((n) => n.id === notification.id, null)
          setUnreadCount((c) => (c === null ? c : c + 1))
          toast.error(getErrorMessage(error, "Não foi possível marcar a notificação como lida"))
        })
    },
    [setReadAt, onBumpsRead],
  )

  const markAllRead = useCallback(() => {
    const previous = list.items
    const previousCount = unreadCount
    const now = new Date().toISOString()
    setReadAt((n) => n.readAt === null, now)
    setUnreadCount(0)
    markAllNotificationsRead()
      .then(() => onBumpsRead(null))
      .catch((error) => {
        setList((prev) => ({ ...prev, items: previous }))
        setUnreadCount(previousCount)
        toast.error(getErrorMessage(error, "Não foi possível marcar as notificações como lidas"))
      })
  }, [list.items, unreadCount, setReadAt, onBumpsRead])

  const markBumpRead = useCallback(
    (dailyId: number) => {
      // the server already read it (bumps/seen); mirror it here and recount
      setReadAt((n) => n.type === "DIARISTA_BUMPED" && n.daily?.id === dailyId && n.readAt === null, new Date().toISOString())
      fetchCount()
    },
    [setReadAt, fetchCount],
  )

  // Read through a ref so the subscription isn't remade whenever markRead changes
  const onPushRef = useRef<(push: NotificationPush) => void>(() => {})
  useEffect(() => {
    onPushRef.current = ({ notification, unreadCount: count }) => {
      setUnreadCount(count)
      // a list not loaded yet gets it when the panel opens
      setList((prev) => (prev.status === "ready" ? { ...prev, items: upsertNotification(prev.items, notification) } : prev))
      if (!notificationToast[notification.type]) return
      const href = notificationView(notification).href
      toast(
        notificationPlainText(notification),
        href
          ? {
              action: {
                label: "Ver",
                onClick: () => {
                  markRead(notification)
                  navigate(href)
                },
              },
            }
          : undefined,
      )
    }
  }, [markRead, navigate])

  useEffect(
    () =>
      subscribe("/user/queue/notifications", (body) => {
        try {
          onPushRef.current(JSON.parse(body) as NotificationPush)
        } catch {
          // ignore malformed frames
        }
      }),
    [subscribe],
  )

  return useMemo(
    () => ({
      unreadCount,
      items: list.items,
      filter: list.filter,
      loading: list.status === "loading" || list.status === "idle",
      error: list.status === "error",
      hasMore: list.hasMore,
      loadingMore: list.loadingMore,
      refresh,
      setFilter,
      loadMore,
      markRead,
      markAllRead,
      markBumpRead,
    }),
    [unreadCount, list, refresh, setFilter, loadMore, markRead, markAllRead, markBumpRead],
  )
}
