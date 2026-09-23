package com.studio.booking.membership.api;

import com.studio.booking.membership.domain.CreditTransaction;
import com.studio.booking.membership.domain.Membership;
import com.studio.booking.membership.infrastructure.CreditTransactionRepository;
import com.studio.booking.membership.infrastructure.MembershipRepository;
import com.studio.booking.shared.error.ApiException;
import com.studio.booking.shared.error.ErrorCode;
import com.studio.booking.shared.validation.ValidUuid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/memberships")
@Tag(name = "Credit Ledger", description = "Credit transaction ledger and balance reconciliation")
public class CreditLedgerController {

    private final MembershipRepository membershipRepository;
    private final CreditTransactionRepository creditTransactionRepository;

    public CreditLedgerController(
        MembershipRepository membershipRepository,
        CreditTransactionRepository creditTransactionRepository
    ) {
        this.membershipRepository = membershipRepository;
        this.creditTransactionRepository = creditTransactionRepository;
    }

    @GetMapping("/{membershipId}/ledger")
    @Operation(
        summary = "Retrieve credit transaction ledger for a membership",
        description = "Returns the append-only credit transaction ledger for a membership, " +
                      "including the current balance reconciliation. " +
                      "For unlimited memberships, returns empty content with null credit figures. " +
                      "Transactions are returned in oldest-first order (by creation time)."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Ledger retrieved successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = CreditLedgerResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Membership not found",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "422",
            description = "Invalid membership ID format",
            content = @Content(mediaType = "application/json")
        )
    })
    public ResponseEntity<CreditLedgerResponse> getLedger(
        @PathVariable @ValidUuid UUID membershipId
    ) {
        Membership membership = membershipRepository.findById(membershipId)
            .orElseThrow(() -> new ApiException(
                ErrorCode.MEMBERSHIP_NOT_FOUND,
                "No membership exists for the given ID",
                null
            ));

        // For unlimited memberships, return empty ledger with null credit figures
        if (membership.isUnlimited()) {
            return ResponseEntity.ok(new CreditLedgerResponse(
                null,
                null,
                true,
                List.of()
            ));
        }

        List<CreditTransaction> transactions = creditTransactionRepository.findByMembershipIdOrderByCreatedAtAsc(membershipId);
        List<CreditLedgerResponse.Entry> entries = transactions.stream()
            .map(txn -> new CreditLedgerResponse.Entry(
                txn.getId().toString(),
                txn.getDelta(),
                txn.getReason(),
                txn.getBalanceAfter(),
                txn.getCreatedAt().toString()
            ))
            .toList();

        return ResponseEntity.ok(new CreditLedgerResponse(
            membership.getCreditsInitial(),
            membership.getCreditsRemaining(),
            true,
            entries
        ));
    }
}
