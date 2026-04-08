-- Add missing updated_at column to pending_invitation table
-- The PendingInvitationEntity extends BaseEntity which requires both created_at and updated_at
ALTER TABLE pending_invitation ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
