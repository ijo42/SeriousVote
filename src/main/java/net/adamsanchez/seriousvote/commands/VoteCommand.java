package net.adamsanchez.seriousvote.commands;

import net.adamsanchez.seriousvote.Data.PlayerRecord;
import net.adamsanchez.seriousvote.Data.VoteSpreeSystem;
import net.adamsanchez.seriousvote.SeriousVote;
import net.adamsanchez.seriousvote.integration.PlaceHolders;
import net.adamsanchez.seriousvote.utils.CM;
import net.adamsanchez.seriousvote.utils.OutputHelper;
import net.adamsanchez.seriousvote.utils.U;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.util.annotation.NonnullByDefault;

/**
 * Created by Adam Sanchez on 4/13/2018.
 */
public class VoteCommand implements CommandExecutor {
    final SeriousVote sv = SeriousVote.getInstance();

    @Override
    @NonNull
    @NonnullByDefault
    public CommandResult execute(CommandSource src, CommandContext args) {
        if (PlaceHolders.apiLoaded) {
            src.sendMessage(PlaceHolders.papiParse(
                    CM.getVoteSiteMessage(sv.getMainCfgNode()),
                    src,
                    src
            ));
        } else {
            src.sendMessage(
                    OutputHelper.strToText(CM.getVoteSiteMessage(sv.getMainCfgNode()))
            );
        }
        CM.getVoteSites(sv.getMainCfgNode()).forEach(site -> src.sendMessage(U.convertStringToLink(site)));

        if (sv.usingVoteSpreeSystem() && (sv.isDailiesEnabled() || sv.isMilestonesEnabled())) {
            if (SeriousVote.getUserStorage().flatMap(userStorage -> userStorage.get(src.getName())).isPresent()) {
                String playerIdentifier = U.getPlayerIdentifier(src.getName());
                PlayerRecord record = sv.getVoteSpreeSystem().getRecord(playerIdentifier);
                if (record != null) {
                    src.sendMessage(Text.of("You have a total of " + record.getTotalVotes()
                            + " votes. You have currently voted " + record.getVoteSpree()
                            + " days in a row.").toBuilder().color(TextColors.GOLD).build());
                    if (sv.isDailiesEnabled()) {
                        int spree = record.getVoteSpree();
                        if (spree != 0) {
                            src.sendMessage(Text.of("You have to vote " + VoteSpreeSystem.getRemainingDays(spree) + " more days until your next dailies reward."));
                        } else {
                            src.sendMessage(Text.of("You have to vote 7 more days until your next dailies reward."));
                        }
                    }
                }

            }
        }
        return CommandResult.success();
    }
}