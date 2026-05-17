package eu.appbahn.platform.api.invite;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Validated
@Tag(name = "Invites")
public interface InvitesApi {

    /**
     * GET /users/me/invites : List pending workspace invitations for the current user
     *
     * @return List of pending invitations (status code 200)
     *         or Unauthorized (status code 401)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/users/me/invites",
            produces = {"application/json"})
    ResponseEntity<List<WorkspaceInvite>> listMyInvites();

    /**
     * POST /invites/{id}/accept : Accept a workspace invitation
     *
     * @param id invite id (required)
     * @return Accepted invitation (status code 200)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.POST,
            value = "/invites/{id}/accept",
            produces = {"application/json"})
    ResponseEntity<WorkspaceInvite> acceptInvite(@PathVariable("id") UUID id);

    /**
     * POST /invites/{id}/decline : Decline a workspace invitation
     *
     * @param id invite id (required)
     * @return Declined (status code 204)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.POST,
            value = "/invites/{id}/decline",
            produces = {"application/json"})
    ResponseEntity<Void> declineInvite(@PathVariable("id") UUID id);

    /**
     * POST /invites/redeem : Redeem an invite code
     *
     * @param redeemInviteRequest the code to redeem (required)
     * @return Redeemed — returns the new workspace membership view (status code 200)
     *         or Bad request / expired / exhausted (status code 400)
     *         or Unauthorized (status code 401)
     *         or Conflict — already a member (status code 409)
     */
    @RequestMapping(
            method = RequestMethod.POST,
            value = "/invites/redeem",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<WorkspaceInvite> redeemInvite(@Valid @RequestBody RedeemInviteRequest redeemInviteRequest);
}
