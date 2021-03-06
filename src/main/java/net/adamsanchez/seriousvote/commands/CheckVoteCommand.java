package net.adamsanchez.seriousvote.commands;

import net.adamsanchez.seriousvote.Data.PlayerRecord;
import net.adamsanchez.seriousvote.SeriousVote;
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
public class CheckVoteCommand implements CommandExecutor {

    @Override
    @NonNull
    @NonnullByDefault
    public CommandResult execute(CommandSource src, CommandContext args) {
        SeriousVote sv = SeriousVote.getInstance();
        String username = args.<String>getOne("player").get();
        String playerIdentifier = U.getPlayerIdentifier(username);
        if (sv.usingVoteSpreeSystem() && (sv.isDailiesEnabled() || sv.isMilestonesEnabled())) {
            PlayerRecord record = sv.getVoteSpreeSystem().getRecord(playerIdentifier);
            if (record != null) {
                src.sendMessage(Text.of(username + " has a total of " + record.getTotalVotes()
                        + " votes. They have currently voted " + record.getVoteSpree()
                        + " days in a row.").toBuilder().color(TextColors.GOLD).build());
                if (sv.isDailiesEnabled()) {
                    int vsa = record.getVoteSpree() + 1;
                    int a = 365 * (vsa / 365 + 1) - vsa;
                    int b = 30 * (vsa / 30 + 1) - vsa;
                    int c = 7 * (vsa / 7 + 1) - vsa;
                    int leastDays = 0;
                    if (a < b && a < c) {
                        leastDays = a;
                    } else if (b < c && b < a) {
                        leastDays = b;
                    } else if (c < b && c < a) {
                        leastDays = c;
                    }
                    src.sendMessage(Text.of("They have to vote " + leastDays + " More days until their next dailies reward."));
                }
            }

        } else {
            src.sendMessage(Text.of("It seems that currently all the database modules are currently disabled."));
        }

        return CommandResult.success();
    }
    }
