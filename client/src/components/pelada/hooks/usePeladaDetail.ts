import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { getPelada, getRanking, getPeladaAwards } from "@/api/peladas";
import { getDailiesForPelada } from "@/api/dailies";
import { getErrorStatus } from "@/lib/errors";
import type { PeladaDetail, PeladaAwards } from "@/types/pelada";
import type { DailyListItem, RankingDTO } from "@/types/daily";

export function usePeladaDetail(id: string | undefined) {
  const [pelada, setPelada] = useState<PeladaDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [accessDenied, setAccessDenied] = useState(false);
  const [error, setError] = useState<unknown>(null);

  const [dailies, setDailies] = useState<DailyListItem[]>([]);
  const [dailiesLoading, setDailiesLoading] = useState(true);

  const [ranking, setRanking] = useState<RankingDTO[]>([]);
  const [rankingLoading, setRankingLoading] = useState(true);

  const [awards, setAwards] = useState<PeladaAwards | null>(null);
  const [awardsLoading, setAwardsLoading] = useState(true);

  // Each fetch returns its promise so callers can await the reload
  const fetchPelada = useCallback(async () => {
    if (!id) return;
    return getPelada(Number(id))
      .then((data) => {
        setPelada(data);
        setError(null);
        setAccessDenied(false);
      })
      .catch((err) => {
        if (getErrorStatus(err) === 403) {
          setAccessDenied(true);
        } else {
          setError(err);
          toast.error("Não foi possível carregar a pelada");
        }
      })
      .finally(() => setLoading(false));
  }, [id]);

  const fetchDailies = useCallback(async () => {
    if (!id) return;
    return getDailiesForPelada(Number(id))
      .then(setDailies)
      .catch(() => { toast.error("Não foi possível carregar as sessões"); })
      .finally(() => setDailiesLoading(false));
  }, [id]);

  const fetchRanking = useCallback(async () => {
    if (!id) return;
    return getRanking(Number(id))
      .then(setRanking)
      .catch(() => { toast.error("Não foi possível carregar o ranking"); })
      .finally(() => setRankingLoading(false));
  }, [id]);

  const fetchAwards = useCallback(async () => {
    if (!id) return;
    return getPeladaAwards(Number(id))
      .then(setAwards)
      .catch(() => { toast.error("Não foi possível carregar os prêmios"); })
      .finally(() => setAwardsLoading(false));
  }, [id]);

  useEffect(() => {
    void fetchPelada();
  }, [fetchPelada]);

  useEffect(() => {
    void fetchDailies();
  }, [fetchDailies]);

  useEffect(() => {
    void fetchRanking();
  }, [fetchRanking]);

  useEffect(() => {
    void fetchAwards();
  }, [fetchAwards]);

  const refetch = useCallback(async () => {
    await Promise.all([fetchPelada(), fetchDailies(), fetchRanking(), fetchAwards()]);
  }, [fetchPelada, fetchDailies, fetchRanking, fetchAwards]);

  return {
    pelada,
    dailies,
    ranking,
    awards,
    loading,
    rankingLoading,
    dailiesLoading,
    awardsLoading,
    accessDenied,
    error,
    refetch,
    refetchPelada: fetchPelada,
    refetchDailies: fetchDailies,
  };
}
