package eu.appbahn.platform.workspace.service;

import eu.appbahn.platform.user.event.UserCreatedEvent;
import eu.appbahn.platform.workspace.entity.WorkspaceMemberEntity;
import eu.appbahn.platform.workspace.repository.PendingInvitationRepository;
import eu.appbahn.platform.workspace.repository.WorkspaceMemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class InvitationAutoConversionListener {

    private static final Logger log = LoggerFactory.getLogger(InvitationAutoConversionListener.class);

    private final PendingInvitationRepository pendingInvitationRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    public InvitationAutoConversionListener(
            PendingInvitationRepository pendingInvitationRepository,
            WorkspaceMemberRepository workspaceMemberRepository) {
        this.pendingInvitationRepository = pendingInvitationRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
    }

    @TransactionalEventListener
    public void onUserCreated(UserCreatedEvent event) {
        var invitations = pendingInvitationRepository.findByEmail(event.email());
        if (invitations.isEmpty()) {
            return;
        }

        log.info("Auto-converting {} pending invitation(s) for {}", invitations.size(), event.email());
        for (var invitation : invitations) {
            var member = new WorkspaceMemberEntity();
            member.setWorkspaceId(invitation.getWorkspaceId());
            member.setUserId(event.userId());
            member.setRole(invitation.getRole());
            workspaceMemberRepository.save(member);
            pendingInvitationRepository.delete(invitation);
        }
    }
}
