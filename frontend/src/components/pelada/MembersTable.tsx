import { Link } from "react-router-dom";
import { Plus, Crown, ShieldCheck, ShieldOff, User, MoreVertical, Trash2 } from "lucide-react";
import { Avatar, AvatarImage, AvatarFallback } from "../ui/avatar";
import { Badge } from "../ui/badge";
import { Button } from "../ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "../ui/dropdown-menu";
import { getPeladaGradient, getFileUrl } from "@/lib/utils";
import type { PeladaMember } from "@/types/pelada";

interface MembersTabProps {
  members: PeladaMember[];
  creatorId: number | null;
  isCurrentUserAdmin: boolean;
  togglingAdmin: number | null;
  onAddPlayer: () => void;
  onToggleAdmin: (member: PeladaMember) => void;
  onRemoveMember: (member: PeladaMember) => void;
}

export function MembersTable({
  members,
  creatorId,
  isCurrentUserAdmin,
  togglingAdmin,
  onAddPlayer,
  onToggleAdmin,
  onRemoveMember,
}: MembersTabProps) {
  return (
    <>
      <div className="flex items-center justify-between mb-3 mt-2">
        <span className="text-sm text-muted-foreground">
          {members.length} membros
        </span>
        {isCurrentUserAdmin && (
          <Button className="bg-gradient-primary rounded-full" size="icon" onClick={onAddPlayer}>
            <Plus className="h-5 w-5 text-white" />
          </Button>
        )}
      </div>

      <div className="space-y-3">
        {members.map((member) => (
          <MemberItem
            key={member.id}
            member={member}
            isCreator={member.id === creatorId}
            canManage={isCurrentUserAdmin && member.id !== creatorId}
            isToggling={togglingAdmin === member.id}
            onToggleAdmin={() => onToggleAdmin(member)}
            onRemove={() => onRemoveMember(member)}
          />
        ))}
      </div>
    </>
  );
}

// Sub-componente para organizar o item da lista
function MemberItem({
  member,
  isCreator,
  canManage,
  isToggling,
  onToggleAdmin,
  onRemove,
}: {
  member: PeladaMember;
  isCreator: boolean;
  canManage: boolean;
  isToggling: boolean;
  onToggleAdmin: () => void;
  onRemove: () => void;
}) {
  const initials = member.username.slice(0, 2).toUpperCase();

  return (
    <div className="flex items-center gap-3">
      <Avatar className="flex-shrink-0">
        {member.image && (
          <AvatarImage src={getFileUrl(member.image)} alt={member.username} />
        )}
        <AvatarFallback
          className={`${getPeladaGradient(member.username)} text-white text-sm font-semibold`}
        >
          {initials}
        </AvatarFallback>
      </Avatar>

      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 flex-wrap">
          <Link
            to={`/profile/${member.id}`}
            className="font-medium truncate hover:underline"
          >
            {member.username}
          </Link>
          {member.position && (
            <Badge variant="secondary" className="text-xs">
              {member.position}
            </Badge>
          )}
        </div>
      </div>

      <div className="flex items-center gap-2 flex-shrink-0">
        {isCreator ? (
          <Crown className="h-4 w-4 text-yellow-500" />
        ) : member.isAdmin ? (
          <ShieldCheck className="h-4 w-4 text-blue-500" />
        ) : (
          <User className="h-4 w-4 text-muted-foreground" />
        )}

        {canManage && (
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <button
                className="p-1.5 rounded hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                disabled={isToggling}
              >
                <MoreVertical className="h-4 w-4" />
              </button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem onClick={onToggleAdmin} disabled={isToggling}>
                {member.isAdmin ? <ShieldOff className="h-4 w-4 mr-1" /> : <ShieldCheck className="h-4 w-4 mr-1" />}
                {member.isAdmin ? "Remover Admin" : "Tornar Admin"}
              </DropdownMenuItem>
              <DropdownMenuItem
                onClick={onRemove}
                className="text-destructive focus:text-destructive"
              >
                <Trash2 className="h-4 w-4 mr-1" />
                Remover da Pelada
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        )}
      </div>
    </div>
  );
}