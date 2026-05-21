package com.example.demo.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.model.Registration;
import com.example.demo.model.User;
import com.example.demo.repository.RegistrationRepository;
import com.example.demo.repository.UserRepository;

@Service
public class PremiumMembershipService {

    private static final Logger log = LoggerFactory.getLogger(PremiumMembershipService.class);

    private final UserRepository userRepository;
    private final RegistrationRepository registrationRepository;
    private final RazorpayPaymentService razorpayPaymentService;
    private final PremiumPurchaseEmailService premiumPurchaseEmailService;

    public PremiumMembershipService(
        UserRepository userRepository,
        RegistrationRepository registrationRepository,
        RazorpayPaymentService razorpayPaymentService,
        PremiumPurchaseEmailService premiumPurchaseEmailService
    ) {
        this.userRepository = userRepository;
        this.registrationRepository = registrationRepository;
        this.razorpayPaymentService = razorpayPaymentService;
        this.premiumPurchaseEmailService = premiumPurchaseEmailService;
    }

    public int durationMonthsForPlan(String plan) {
        if (plan == null) return 0;
        String p = plan.trim().toLowerCase(Locale.ROOT);
        return switch (p) {
            case "monthly" -> 1;
            case "quarterly" -> 3;
            case "half-yearly" -> 6;
            case "yearly" -> 12;
            default -> 0;
        };
    }

    @Transactional
    public boolean applyVerifiedPremiumPayment(
        User user,
        String plan,
        String orderId,
        String paymentId,
        String source
    ) {
        if (user == null) throw new IllegalArgumentException("user is required");
        if (plan == null || plan.isBlank()) throw new IllegalArgumentException("plan is required");
        if (orderId == null || orderId.isBlank()) throw new IllegalArgumentException("orderId is required");
        if (paymentId == null || paymentId.isBlank()) throw new IllegalArgumentException("paymentId is required");

        User lockedUser = userRepository.findByIdForUpdate(user.getId())
            .orElseThrow(() -> new IllegalArgumentException("user not found"));

        int months = durationMonthsForPlan(plan);
        if (months <= 0) throw new IllegalArgumentException("Invalid plan: " + plan);

        // Idempotency per-user: if webhook + checkout handler both try to apply,
        // the second attempt will see the stored paymentId and no-op.
        if (paymentId.equals(lockedUser.getMembershipPaymentId())) {
            return false;
        }

        Integer amountInr;
        try {
            amountInr = razorpayPaymentService.premiumAmountInInr(plan);
        } catch (Exception ex) {
            // Fallback: keep amount unknown rather than failing the membership apply.
            amountInr = null;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime base = now;
        if (lockedUser.getMembershipExpireTime() != null && lockedUser.getMembershipExpireTime().isAfter(now)) {
            // Renewal should extend from current expiry.
            base = lockedUser.getMembershipExpireTime();
        }

        lockedUser.setMembership("Premium");
        lockedUser.setMembershipPurchaseTime(now);
        lockedUser.setMembershipExpireTime(base.plusMonths(months));
        lockedUser.setMembershipPlan(plan.trim().toLowerCase(Locale.ROOT));
        lockedUser.setMembershipAmountPaidInr(amountInr);
        lockedUser.setMembershipPaymentId(paymentId);
        userRepository.save(lockedUser);

        // Best-effort sync of registration rows
        try {
            List<Registration> regs = registrationRepository.findByUserId(user.getId());
            if (regs != null && !regs.isEmpty()) {
                for (Registration r : regs) {
                    r.setMembership("Premium");
                }
                registrationRepository.saveAll(regs);
            }
        } catch (Exception syncEx) {
            log.warn("Failed to sync registration membership after premium apply (userId={})", user.getId(), syncEx);
        }

        // Best-effort email (do NOT fail membership apply if email fails)
        try {
            premiumPurchaseEmailService.trySendPremiumPurchaseEmail(lockedUser);
        } catch (Exception emailEx) {
            log.warn("Premium purchase email send failed after membership apply (userId={})", user.getId(), emailEx);
        }

        return true;
    }
}
