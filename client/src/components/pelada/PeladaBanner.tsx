import { Calendar, Clock, MapPin, Bookmark, Pencil, Trash2 } from "lucide-react";
import { getPeladaInitials, getPeladaGradient } from "../../lib/utils";
import type { PeladaDetail } from "../../types/pelada";

interface PeladaBannerProps {
  pelada: PeladaDetail;
  isCurrentUserAdmin: boolean;
  isCurrentUserCreator: boolean;
  onEdit: () => void;
  onDelete: () => void;
  getFileUrl: (path: string | null | undefined) => string | undefined;
}

export function PeladaBanner({
  pelada,
  isCurrentUserAdmin,
  isCurrentUserCreator,
  onEdit,
  onDelete,
  getFileUrl,
}: PeladaBannerProps) {
  return (
    <div className="relative h-72">
      {pelada.image ? (
        <img
          src={getFileUrl(pelada.image)}
          alt={pelada.name}
          className="h-full w-full object-cover"
        />
      ) : (
        <div
          className={`h-full w-full ${getPeladaGradient(pelada.name)} flex items-center justify-center`}
        >
          <span className="text-6xl font-extrabold text-white tracking-wide select-none">
            {getPeladaInitials(pelada.name)}
          </span>
        </div>
      )}
      
      <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/30 to-transparent" />

      {/* Metadata overlay — bottom */}
      <div className="absolute bottom-0 left-0 right-0 px-4 pb-4">
        <h1 className="text-2xl font-bold text-white mb-1">
          {pelada.name}
        </h1>
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-white/80">
          <span className="flex items-center gap-1">
            <Calendar className="h-3.5 w-3.5 shrink-0" />
            {pelada.dayOfWeek}
          </span>
          <span className="flex items-center gap-1">
            <Clock className="h-3.5 w-3.5 shrink-0" />
            {pelada.timeOfDay} · {pelada.duration}h
          </span>
          {pelada.address && (
            <span className="flex items-center gap-1">
              <MapPin className="h-3.5 w-3.5 shrink-0" />
              {pelada.address}
            </span>
          )}
          {pelada.reference && (
            <span className="flex items-center gap-1">
              <Bookmark className="h-3.5 w-3.5 shrink-0" />
              {pelada.reference}
            </span>
          )}
        </div>
      </div>

      {/* Admin/Creator Controls */}
      {(isCurrentUserAdmin || isCurrentUserCreator) && (
        <div className="absolute top-3 right-3 flex items-center gap-2">
          {isCurrentUserAdmin && (
            <button
              aria-label="Edit pelada"
              className="p-2 rounded-full bg-black/40 text-white hover:bg-black/60 transition-colors"
              onClick={onEdit}
            >
              <Pencil className="h-4 w-4" />
            </button>
          )}
          {isCurrentUserCreator && (
            <button
              aria-label="Delete pelada"
              className="p-2 rounded-full bg-black/40 text-white hover:bg-black/60 transition-colors"
              onClick={onDelete}
            >
              <Trash2 className="h-4 w-4" />
            </button>
          )}
        </div>
      )}
    </div>
  );
}