---
title: Inviting Collaborators
description: How to invite users to a workspace using email invitations or invite codes.
---

AppBahn provides two ways to add collaborators to a workspace: direct email invitations and reusable invite codes.

## Viewing members

The workspace dashboard shows a **Members** panel listing everyone in the workspace at a glance — each with their avatar, name, and role. Use **Manage** to open **Settings → Members**, where you can invite, change roles, or remove members.

## Email invitations

Workspace Admins can invite a user by email from **Settings → Members**. If the user already has an account, they become an Active member immediately. If the user does not yet have an account, a pending invitation is created; it is automatically converted to an Active membership when they sign up.

## Accepting or declining an invitation

When you have a pending invitation, the workspace appears on the **Workspaces** page with Accept and Decline buttons. Invitations may carry an expiry date; expired invitations are not shown.

- **Accept** — joins the workspace with the assigned role and redirects you to the workspace.
- **Decline** — removes the invitation without joining.

## Invite codes

Workspace Owners can mint reusable invite codes from **Settings → Invites**. A code grants the holder a specific role in the workspace and can be used a configurable number of times. Optionally, a code can be set to expire after a given date.

To mint a code:

1. Open **Settings → Invites** for the workspace.
2. Click **Mint Code** and select a role, maximum uses, and optional expiry.
3. Copy the generated code (format: `abp_<12 chars>`) and share it out-of-band.

To revoke a code before it is fully used, click **Revoke** next to the code in the list.

## Joining with a code

Anyone with a valid code can join a workspace:

1. On the **Workspaces** page, click **Join with code**.
2. Enter the code in the dialog and confirm.
3. You are added to the workspace with the role encoded in the code and redirected there.

Expired or fully-redeemed codes return an error.

## Roles

The available roles are `OWNER`, `ADMIN`, `EDITOR`, and `VIEWER`. See [Concepts](./concepts.md) for the permission matrix.
