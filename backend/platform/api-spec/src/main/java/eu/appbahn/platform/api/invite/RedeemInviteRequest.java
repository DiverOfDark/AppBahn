package eu.appbahn.platform.api.invite;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RedeemInviteRequest {

    @NotBlank
    private String code;
}
