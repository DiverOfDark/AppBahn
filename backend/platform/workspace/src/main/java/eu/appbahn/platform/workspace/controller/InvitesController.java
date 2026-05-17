package eu.appbahn.platform.workspace.controller;

import eu.appbahn.platform.api.invite.InvitesApi;
import eu.appbahn.platform.api.invite.RedeemInviteRequest;
import eu.appbahn.platform.api.invite.WorkspaceInvite;
import eu.appbahn.platform.common.security.AuthContextHolder;
import eu.appbahn.platform.workspace.service.InviteService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class InvitesController implements InvitesApi {

    private final InviteService inviteService;

    public InvitesController(InviteService inviteService) {
        this.inviteService = inviteService;
    }

    @Override
    public ResponseEntity<List<WorkspaceInvite>> listMyInvites() {
        return ResponseEntity.ok(inviteService.listMyInvites(AuthContextHolder.get()));
    }

    @Override
    public ResponseEntity<WorkspaceInvite> acceptInvite(UUID id) {
        return ResponseEntity.ok(inviteService.acceptInvite(id, AuthContextHolder.get()));
    }

    @Override
    public ResponseEntity<Void> declineInvite(UUID id) {
        inviteService.declineInvite(id, AuthContextHolder.get());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<WorkspaceInvite> redeemInvite(RedeemInviteRequest redeemInviteRequest) {
        return ResponseEntity.ok(inviteService.redeemCode(redeemInviteRequest, AuthContextHolder.get()));
    }
}
