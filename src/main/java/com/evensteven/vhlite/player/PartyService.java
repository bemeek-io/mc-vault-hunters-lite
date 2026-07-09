package com.evensteven.vhlite.player;

import com.evensteven.vhlite.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lightweight in-memory parties: a leader plus members, joined by /vh party
 * invite + accept. Whoever activates a crystal at the altar brings their
 * whole party into the vault. Parties don't survive restarts on purpose —
 * they're a session concept.
 */
public final class PartyService {

    private final int maxSize;
    /** member -> leader (leaders map to themselves). */
    private final Map<UUID, UUID> partyOf = new HashMap<>();
    /** invited -> leader who invited them (latest wins). */
    private final Map<UUID, UUID> invites = new HashMap<>();

    public PartyService(int maxSize) {
        this.maxSize = Math.max(1, maxSize);
    }

    /** Everyone in the player's party (including them), online only. */
    public List<Player> membersOf(Player player) {
        UUID leader = partyOf.getOrDefault(player.getUniqueId(), player.getUniqueId());
        List<Player> out = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (Map.Entry<UUID, UUID> entry : partyOf.entrySet()) {
            if (entry.getValue().equals(leader) && seen.add(entry.getKey())) {
                Player member = Bukkit.getPlayer(entry.getKey());
                if (member != null && member.isOnline()) {
                    out.add(member);
                }
            }
        }
        if (out.isEmpty()) {
            out.add(player); // solo
        }
        return out;
    }

    public void invite(Player leader, Player target) {
        if (leader.equals(target)) {
            leader.sendMessage(Text.c("§7That's you."));
            return;
        }
        UUID leaderParty = partyOf.getOrDefault(leader.getUniqueId(), leader.getUniqueId());
        if (!leaderParty.equals(leader.getUniqueId())) {
            leader.sendMessage(Text.c("§cOnly the party leader can invite."));
            return;
        }
        if (membersOf(leader).size() >= maxSize) {
            leader.sendMessage(Text.c("§cYour party is full (max " + maxSize + ")."));
            return;
        }
        if (partyOf.containsKey(target.getUniqueId())) {
            leader.sendMessage(Text.c("§c" + target.getName() + " is already in a party."));
            return;
        }
        // Ensure the leader is registered as their own party's member.
        partyOf.putIfAbsent(leader.getUniqueId(), leader.getUniqueId());
        invites.put(target.getUniqueId(), leader.getUniqueId());
        leader.sendMessage(Text.c("§aInvited §e" + target.getName() + "§a."));
        target.sendMessage(Text.c("§e" + leader.getName()
                + " §7invited you to a vault party. §a/vh party accept"));
    }

    public void accept(Player player) {
        UUID leaderId = invites.remove(player.getUniqueId());
        if (leaderId == null) {
            player.sendMessage(Text.c("§7No pending invite."));
            return;
        }
        Player leader = Bukkit.getPlayer(leaderId);
        if (leader == null || !leader.isOnline()) {
            player.sendMessage(Text.c("§7The inviter went offline."));
            return;
        }
        if (membersOf(leader).size() >= maxSize) {
            player.sendMessage(Text.c("§cThat party filled up."));
            return;
        }
        partyOf.put(player.getUniqueId(), leaderId);
        for (Player member : membersOf(leader)) {
            member.sendMessage(Text.c("§e" + player.getName() + " §7joined the party."));
        }
    }

    public void decline(Player player) {
        if (invites.remove(player.getUniqueId()) != null) {
            player.sendMessage(Text.c("§7Invite declined."));
        } else {
            player.sendMessage(Text.c("§7No pending invite."));
        }
    }

    public void leave(Player player) {
        UUID id = player.getUniqueId();
        UUID leader = partyOf.get(id);
        if (leader == null) {
            player.sendMessage(Text.c("§7You are not in a party."));
            return;
        }
        if (leader.equals(id)) {
            // Leader leaving disbands the party.
            for (Player member : membersOf(player)) {
                partyOf.remove(member.getUniqueId());
                member.sendMessage(Text.c("§7The party was disbanded."));
            }
        } else {
            partyOf.remove(id);
            player.sendMessage(Text.c("§7You left the party."));
            Player lead = Bukkit.getPlayer(leader);
            if (lead != null) {
                lead.sendMessage(Text.c("§e" + player.getName() + " §7left the party."));
            }
        }
    }

    public void kick(Player leader, Player target) {
        UUID leaderParty = partyOf.getOrDefault(leader.getUniqueId(), leader.getUniqueId());
        if (!leaderParty.equals(leader.getUniqueId())) {
            leader.sendMessage(Text.c("§cOnly the party leader can kick."));
            return;
        }
        if (!leader.getUniqueId().equals(partyOf.get(target.getUniqueId()))) {
            leader.sendMessage(Text.c("§c" + target.getName() + " is not in your party."));
            return;
        }
        partyOf.remove(target.getUniqueId());
        target.sendMessage(Text.c("§7You were removed from the party."));
        leader.sendMessage(Text.c("§7Removed §e" + target.getName() + "§7."));
    }

    public void handleQuit(Player player) {
        invites.remove(player.getUniqueId());
        UUID leader = partyOf.get(player.getUniqueId());
        if (leader != null && leader.equals(player.getUniqueId())) {
            for (Player member : membersOf(player)) {
                if (!member.equals(player)) {
                    member.sendMessage(Text.c("§7The party leader left; party disbanded."));
                }
                partyOf.remove(member.getUniqueId());
            }
        }
        partyOf.remove(player.getUniqueId());
    }

    public String describe(Player player) {
        List<Player> members = membersOf(player);
        if (members.size() <= 1 && !partyOf.containsKey(player.getUniqueId())) {
            return "§7You are not in a party. §e/vh party invite <player>";
        }
        StringBuilder sb = new StringBuilder("§7Party: ");
        for (Player member : members) {
            sb.append("§e").append(member.getName()).append("§7, ");
        }
        return sb.substring(0, sb.length() - 4);
    }
}
