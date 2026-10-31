-- Additive consent-based membership. Existing memberships and sporting history are unchanged.
CREATE TABLE pelada_invitations (
    id BIGSERIAL PRIMARY KEY,
    pelada_id BIGINT NOT NULL REFERENCES peladas(id) ON DELETE CASCADE,
    recipient_id BIGINT NOT NULL REFERENCES users(id),
    sender_id BIGINT NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELED', 'EXPIRED')),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    responded_at TIMESTAMP(6) WITH TIME ZONE,
    canceled_at TIMESTAMP(6) WITH TIME ZONE,
    CHECK (expires_at > created_at)
);
CREATE UNIQUE INDEX uq_pelada_invitations_pending ON pelada_invitations(pelada_id, recipient_id) WHERE status = 'PENDING';
CREATE INDEX idx_invitations_pelada_created ON pelada_invitations(pelada_id, created_at DESC, id DESC);
CREATE INDEX idx_invitations_recipient ON pelada_invitations(recipient_id, status, expires_at);
CREATE INDEX idx_invitations_sender ON pelada_invitations(sender_id);
CREATE INDEX idx_invitations_declined ON pelada_invitations(pelada_id, recipient_id, responded_at) WHERE status = 'DECLINED';

CREATE TABLE pelada_invite_links (
    id BIGSERIAL PRIMARY KEY,
    pelada_id BIGINT NOT NULL REFERENCES peladas(id) ON DELETE CASCADE,
    creator_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP(6) WITH TIME ZONE,
    CHECK (expires_at > created_at)
);
-- Expired rows are revoked before replacement; GET never needs to materialize expiry.
CREATE UNIQUE INDEX uq_pelada_invite_links_active ON pelada_invite_links(pelada_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_invite_links_creator ON pelada_invite_links(creator_id);

CREATE TABLE pelada_link_blocks (
    id BIGSERIAL PRIMARY KEY,
    pelada_id BIGINT NOT NULL REFERENCES peladas(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    UNIQUE(pelada_id, user_id)
);
CREATE INDEX idx_link_blocks_user ON pelada_link_blocks(user_id);

ALTER TABLE notifications ADD COLUMN invitation_id BIGINT REFERENCES pelada_invitations(id) ON DELETE SET NULL;
CREATE INDEX idx_notifications_invitation ON notifications(invitation_id);
ALTER TABLE notifications DROP CONSTRAINT notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check CHECK (type IN (
    'DIARISTA_BUMPED', 'SESSION_CANCELED', 'ATTENDANCE_REMOVED_BY_ADMIN', 'ATTENDANCE_CONFIRMED_BY_ADMIN',
    'SESSION_REMINDER', 'SESSION_CREATED', 'DIARISTA_WINDOW_OPEN', 'TEAMS_SORTED', 'SESSION_FINISHED',
    'ADDED_TO_PELADA', 'REMOVED_FROM_PELADA', 'ADMIN_GRANTED', 'ADMIN_REVOKED', 'MENSALISTA_GRANTED',
    'MENSALISTA_REVOKED', 'PELADA_TRANSFERRED', 'AWARD_WON', 'PELADA_INVITATION', 'MEMBER_JOINED'));
