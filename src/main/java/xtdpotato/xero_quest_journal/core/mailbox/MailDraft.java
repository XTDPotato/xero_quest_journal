package xtdpotato.xero_quest_journal.core.mailbox;

import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import java.util.List;

/** Portable administrator-authored mail, including component-preserving rewards. */
public record MailDraft(String id, String sender, String subject, String body, String icon,
                        boolean treasured, List<QuestDefinition.Reward> rewards,
                        List<MailboxRuntime.Attachment> attachments, long expiresIn, boolean gameTime,
                        String banner, String targetTaskId, MailBannerTiming bannerTiming) {
    public MailDraft(String id, String sender, String subject, String body, String icon,
                     boolean treasured, List<QuestDefinition.Reward> rewards,
                     List<MailboxRuntime.Attachment> attachments, long expiresIn, boolean gameTime,
                     String banner, String targetTaskId) {
        this(id, sender, subject, body, icon, treasured, rewards, attachments, expiresIn, gameTime,
                banner, targetTaskId, MailBannerTiming.NONE);
    }
    public MailDraft(String id, String sender, String subject, String body, String icon,
                     boolean treasured, List<QuestDefinition.Reward> rewards,
                     List<MailboxRuntime.Attachment> attachments, long expiresIn, boolean gameTime) {
        this(id, sender, subject, body, icon, treasured, rewards, attachments, expiresIn, gameTime, "", "");
    }
    public MailDraft(String id, String sender, String subject, String body, String icon,
                     boolean treasured, List<QuestDefinition.Reward> rewards,
                     List<MailboxRuntime.Attachment> attachments) {
        this(id, sender, subject, body, icon, treasured, rewards, attachments, 0, false);
    }
    public MailDraft(String id, String sender, String subject, String body, String icon,
                     boolean treasured, List<QuestDefinition.Reward> rewards) {
        this(id, sender, subject, body, icon, treasured, rewards, null);
    }

    public MailDraft {
        if (id == null || sender == null || subject == null || body == null || icon == null || rewards == null)
            throw new IllegalArgumentException("Incomplete mail draft");
        rewards = List.copyOf(rewards);
        banner = banner == null ? "" : banner;
        targetTaskId = targetTaskId == null ? "" : targetTaskId;
        bannerTiming = bannerTiming == null ? MailBannerTiming.NONE : bannerTiming;
        if (banner.length() > 8192 || targetTaskId.length() > 256)
            throw new IllegalArgumentException("Mail banner or task reference is too long");
        xtdpotato.xero_quest_journal.core.model.MailNodeSettings.validateValue("expiresIn", Long.toString(expiresIn));
        if (rewards.size() > 128) throw new IllegalArgumentException("Too many mail rewards");
        attachments = attachments == null ? rewards.stream()
                .map(r -> new MailboxRuntime.Attachment(r.type(), r.item(), r.count(), false,
                        false, false, r.quality(), r.background())).toList()
                : List.copyOf(attachments);
        if (attachments.size() > 128) throw new IllegalArgumentException("Too many mail attachments");
    }
}
