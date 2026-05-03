package eu.appbahn.platform.api.imagesource;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@Tag(name = "ImageSourceWebhook")
public interface ImageSourceWebhookApi {

    /**
     * GET /image-sources/{slug}/webhook : GetImageSourceWebhook
     *
     * <p>Returns the inbound webhook URL plus a masked form of the current capability token.
     * The plaintext token is only ever returned by {@link #rotateImageSourceWebhook} on the
     * mint/rotate call — subsequent reads only show the mask.
     *
     * @param slug ImageSource slug
     * @return current webhook config (URL + masked secret); {@code 404} if no token exists yet.
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/image-sources/{slug}/webhook",
            produces = {"application/json"})
    ResponseEntity<ImageSourceWebhookView> getImageSourceWebhook(@PathVariable("slug") String slug);

    /**
     * POST /image-sources/{slug}/webhook/rotate : RotateImageSourceWebhook
     *
     * <p>Mints (or rotates) the per-ImageSource webhook token. The plaintext token is returned
     * exactly once in the response body — subsequent reads only get the masked form. Existing
     * tokens are invalidated immediately on rotate.
     *
     * @param slug ImageSource slug
     * @return new token + URL the user should paste into their CI/git-provider settings.
     */
    @RequestMapping(
            method = RequestMethod.POST,
            value = "/image-sources/{slug}/webhook/rotate",
            produces = {"application/json"})
    ResponseEntity<ImageSourceWebhookSecret> rotateImageSourceWebhook(@PathVariable("slug") String slug);
}
