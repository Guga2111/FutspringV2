import { Users, Plus } from "lucide-react";
import { Button } from "../ui/button";
import { Skeleton } from "../ui/skeleton";
import type { DailyListItem } from "@/types/daily";

const STATUS_DOT: Record<string, string> = {
  IN_COURSE: "bg-green-500", SCHEDULED: "bg-blue-500", FINISHED: "bg-gray-400", CANCELED: "bg-red-500",
};

const STATUS_LABEL: Record<string, string> = {
  IN_COURSE: "Em Curso",
  SCHEDULED: "Agendado",
  CONFIRMED: "Confirmado",
  FINISHED: "Finalizado",
  CANCELED: "Cancelado",
  CANCELLED: "Cancelado",
};

interface SessionsTabProps {
  dailies: DailyListItem[];
  isLoading: boolean;
  isAdmin: boolean;
  onOpenCreate: () => void;
  onNavigate: (id: number) => void;
}

export function SessionsTable({ dailies, isLoading, isAdmin, onOpenCreate, onNavigate }: SessionsTabProps) {

  return (
    <>
      <div className="flex items-center justify-between mb-3 mt-2">
        <span className="text-sm text-muted-foreground">{dailies.length} sessões</span>
        {isAdmin && (
          <Button className="bg-gradient-primary rounded-full" size="icon" onClick={onOpenCreate}>
            <Plus className="h-5 w-5 text-white" />
          </Button>
        )}
      </div>

      {isLoading ? (
        <div className="space-y-3"><Skeleton className="h-10 w-full" /><Skeleton className="h-10 w-full" /></div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b text-muted-foreground text-xs text-left">
                <th className="pb-2">Data</th>
                <th className="pb-2">Status</th>
                <th className="pb-2">Jogadores</th>
              </tr>
            </thead>
            <tbody>
              {dailies.map((daily) => (
                <tr key={daily.id} className="border-b hover:bg-muted/50 cursor-pointer" onClick={() => onNavigate(daily.id)}>
                  <td className="py-3 font-medium">{new Date(daily.dailyDate + 'T12:00:00').toLocaleDateString('pt-BR', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' })}</td>
                  <td className="py-3">
                    <span className="flex items-center gap-1.5">
                      <span className={`h-2 w-2 rounded-full flex-shrink-0 ${STATUS_DOT[daily.status] || "bg-gray-400"}`} />
                      {STATUS_LABEL[daily.status] || daily.status}
                    </span>
                  </td>
                  <td className="py-3 flex items-center gap-1.5 text-muted-foreground">
                    <Users className="h-3.5 w-3.5" /> {daily.confirmedPlayerCount}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}