package com.velorix.sentinel.dto.packet;

import com.velorix.sentinel.entity.enums.NetworkProtocol;
import com.velorix.sentinel.entity.enums.Protocol;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Optional filters for the packet listing. A {@code null} member means "any".
 */
@Schema(name = "PacketFilter", description = "Filters for the packet listing")
public record PacketFilter(
        @Schema(description = "Restrict to one transport or application protocol") Protocol protocol,

        @Schema(description = "Restrict to one network layer family") NetworkProtocol networkProtocol,

        @Schema(description = "Restrict to suspicious or clean frames") Boolean suspicious,

        @Schema(description = "Free text match on either endpoint address or the frame description")
        @Size(max = 100, message = "The search term must not exceed 100 characters")
        String search) {

    /** Normalised SQL LIKE pattern, or null when no search term was supplied. */
    public String likePattern() {
        if (search == null || search.isBlank()) {
            return null;
        }
        return "%" + search.trim().toLowerCase(java.util.Locale.ROOT) + "%";
    }
}
