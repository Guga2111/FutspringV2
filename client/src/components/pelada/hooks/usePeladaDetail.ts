import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { getPelada, getRanking, getPeladaAwards } from "../../../api/peladas";
import { getDailiesForPelada } from "../../../api/dailies";
import type { PeladaDetail, PeladaAwards } from "../../../types/pelada";
import type { DailyListItem, RankingDTO } from "../../../types/daily";

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

  const fetchPelada = useCallback(() => {
    if (!id) return;
    setLoading(true);
    getPelada(Number(id))
      .then((data) => {
        setPelada(data);
        setError(null);
        setAccessDenied(false);
      })
      .catch((err) => {
        if (err?.response?.status === 403) {
          setAccessDenied(true);
        } else {
          setError(err);
          toast.error("Falha ao carregar pelada");
        }
      })
      .finally(() => setLoading(false));
  }, [id]);

  const fetchDailies = useCallback(() => {
    if (!id) return;
    setDailiesLoading(true);
    getDailiesForPelada(Number(id))
      .then(setDailies)
      .catch(() => toast.error("Failed to load sessions"))
      .finally(() => setDailiesLoading(false));
  }, [id]);

  const fetchRanking = useCallback(() => {
    if (!id) return;
    setRankingLoading(true);
    getRanking(Number(id))
      .then(setRanking)
      .catch(() => toast.error("Failed to load ranking"))
      .finally(() => setRankingLoading(false));
  }, [id]);

  const fetchAwards = useCallback(() => {
    if (!id) return;
    setAwardsLoading(true);
    getPeladaAwards(Number(id))
      .then(setAwards)
      .catch(() => toast.error("Failed to load awards"))
      .finally(() => setAwardsLoading(false));
  }, [id]);

  useEffect(() => {
    fetchPelada();
  }, [fetchPelada]);

  useEffect(() => {
    fetchDailies();
  }, [fetchDailies]);

  useEffect(() => {
    fetchRanking();
  }, [fetchRanking]);

  useEffect(() => {
    fetchAwards();
  }, [fetchAwards]);

  const refetch = useCallback(() => {
    fetchPelada();
    fetchDailies();
    fetchRanking();
    fetchAwards();
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
