import { useState } from "react";
import { format } from "date-fns";
import { CalendarIcon } from "lucide-react";
import { toast } from "sonner";
import { createDaily } from "../../api/dailies";
import { Button } from "../ui/button";
import { Calendar } from "../ui/calendar";
import { Popover, PopoverContent, PopoverTrigger } from "../ui/popover";
import { cn } from "../../lib/utils";

export function CreateSessionDialog({
  peladaId,
  onClose,
  onCreated,
}: {
  peladaId: number;
  onClose: () => void;
  onCreated: () => void;
}) {
  const [selectedDate, setSelectedDate] = useState<Date | undefined>();
  const [calendarOpen, setCalendarOpen] = useState(false);
  const [dailyTime, setDailyTime] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const dailyDate = selectedDate ? format(selectedDate, "yyyy-MM-dd") : "";

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!dailyDate || !dailyTime) return;
    setSubmitting(true);
    try {
      await createDaily(peladaId, { dailyDate, dailyTime });
      toast.success("Sessão criada!");
      onCreated();
      onClose();
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } };
      toast.error(e?.response?.data?.message ?? "Falha ao criar uma sessão");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4"
      onClick={onClose}
    >
      <div
        className="bg-background rounded-lg shadow-lg w-full max-w-sm p-6"
        onClick={(e) => e.stopPropagation()}
      >
        <h3 className="text-lg font-semibold mb-4">Criar Sessão</h3>
        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Date — shadcn Calendar in a Popover */}
          <div>
            <label className="block text-sm font-medium mb-1">Data</label>
            <Popover open={calendarOpen} onOpenChange={setCalendarOpen}>
              <PopoverTrigger asChild>
                <Button
                  type="button"
                  variant="outline"
                  className={cn(
                    "w-full justify-start text-left font-normal rounded-md",
                    !selectedDate && "text-muted-foreground"
                  )}
                >
                  <CalendarIcon className="mr-2 h-4 w-4" />
                  {selectedDate ? format(selectedDate, "PPP") : "Escolha uma data"}
                </Button>
              </PopoverTrigger>
              <PopoverContent className="w-auto p-0" align="start">
                <Calendar
                  mode="single"
                  selected={selectedDate}
                  onSelect={(date) => {
                    setSelectedDate(date);
                    setCalendarOpen(false);
                  }}
                  initialFocus
                />
              </PopoverContent>
            </Popover>
          </div>

          {/* Time */}
          <div>
            <label
              className="block text-sm font-medium mb-1"
              htmlFor="session-time"
            >
              Horário
            </label>
            <input
              id="session-time"
              type="time"
              required
              className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm"
              value={dailyTime}
              onChange={(e) => setDailyTime(e.target.value)}
            />
          </div>

          <div className="flex gap-3 justify-end pt-2">
            <button
              type="button"
              className="text-sm text-muted-foreground hover:underline"
              onClick={onClose}
              disabled={submitting}
            >
              Cancelar
            </button>
            <Button
              type="submit"
              className="rounded-full"
              variant="gradient"
              disabled={submitting || !selectedDate || !dailyTime}
            >
              {submitting ? "Criando..." : "Criar"}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
