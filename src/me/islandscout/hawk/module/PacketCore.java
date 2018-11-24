/*
 * This file is part of Hawk Anticheat.
 *
 * Hawk Anticheat is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Hawk Anticheat is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Hawk Anticheat.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.islandscout.hawk.module;

import me.islandscout.hawk.Hawk;
import me.islandscout.hawk.HawkPlayer;
import me.islandscout.hawk.event.AbilitiesEvent;
import me.islandscout.hawk.event.MaterialInteractionEvent;
import me.islandscout.hawk.event.Event;
import me.islandscout.hawk.event.PositionEvent;
import me.islandscout.hawk.listener.PacketListener;
import me.islandscout.hawk.listener.PacketListener7;
import me.islandscout.hawk.listener.PacketListener8;
import me.islandscout.hawk.util.ClientBlock;
import me.islandscout.hawk.util.Debug;
import me.islandscout.hawk.util.packet.PacketAdapter;
import me.islandscout.hawk.util.packet.PacketConverter7;
import me.islandscout.hawk.util.packet.PacketConverter8;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

/**
 * This class is mainly used to process packets that are intercepted from the Netty channels.
 * Remember, caution is advised when accessing the Bukkit API from the Netty thread.
 */
public class PacketCore implements Listener {

    //Welcome to TCP damnation.

    private final int serverVersion;
    private final Hawk hawk;
    private PacketListener packetListener;

    public PacketCore(int serverVersion, Hawk hawk) {
        this.serverVersion = serverVersion;
        this.hawk = hawk;
        try {
            if (serverVersion == 7) {
                packetListener = new PacketListener7(this);
                hawk.getLogger().info("Using NMS 1.7_R4 NIO for packet interception.");
            } else if (serverVersion == 8) {
                packetListener = new PacketListener8(this);
                hawk.getLogger().info("Using NMS 1.8_R3 NIO for packet interception.");
            } else warnConsole(hawk);
        } catch (NoClassDefFoundError e) {
            e.printStackTrace();
            warnConsole(hawk);
        }
        Bukkit.getPluginManager().registerEvents(this, hawk);
    }

    private void warnConsole(Hawk hawk) {
        hawk.getLogger().warning("!!!!!!!!!!");
        hawk.getLogger().warning("It appears that you are not running Hawk on a 1.7.10 or 1.8.8 server.");
        hawk.getLogger().warning("Hawk will NOT work. Please run Hawk on a 1.7_R4 or 1.8_R3 server.");
        hawk.getLogger().warning("!!!!!!!!!!");
        Bukkit.getPluginManager().disablePlugin(hawk);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean process(Object packet, Player p) {
        HawkPlayer pp = hawk.getHawkPlayer(p);

        //ignore packets while player is no longer registered in Hawk
        if (!pp.isOnline())
            return false;

        Event event;
        if (serverVersion == 8)
            event = PacketConverter8.packetToEvent(packet, p, pp);
        else if (serverVersion == 7)
            event = PacketConverter7.packetToEvent(packet, p, pp);
        else
            return true;
        if (event == null)
            return true;

        if (event instanceof PositionEvent) {
            PositionEvent posEvent = (PositionEvent) event;
            posEvent.setTeleported(false);
            pp.incrementCurrentTick();
            //handle teleports
            if (pp.isTeleporting()) {
                Location tpLoc = pp.getTeleportLoc();
                if (tpLoc.getWorld().equals(posEvent.getTo().getWorld()) && posEvent.getTo().distanceSquared(tpLoc) < 0.001) {
                    posEvent.setFrom(tpLoc);
                    pp.setTeleporting(false);
                    posEvent.setTeleported(true);
                } else {
                    //Help guide the confused client back to the tp location
                    if (System.currentTimeMillis() - pp.getLastTeleportTime() > 1000) {
                        pp.teleportPlayer(tpLoc, PlayerTeleportEvent.TeleportCause.PLUGIN);
                    }
                    return false;
                }
            }
            //handle illegal move
            else if (posEvent.getFrom().getWorld().equals(posEvent.getTo().getWorld()) && posEvent.getTo().distanceSquared(posEvent.getFrom()) > 64) {
                hawk.getLogger().warning(p.getName() + " may have tried to crash the server by moving too far! Distance: " + (posEvent.getTo().distance(posEvent.getFrom())));
                posEvent.cancelAndSetBack(p.getLocation());
                return false;
            }
        }

        hawk.getCheckManager().dispatchEvent(event);

        //handle block placing
        if (event instanceof MaterialInteractionEvent && ((MaterialInteractionEvent) event).getInteractionType() == MaterialInteractionEvent.InteractionType.PLACE_BLOCK) {
            MaterialInteractionEvent bPlaceEvent = (MaterialInteractionEvent) event;
            if (!bPlaceEvent.isCancelled()) {
                ClientBlock clientBlock = new ClientBlock(bPlaceEvent.getPlacedBlockLocation(), pp.getCurrentTick(), bPlaceEvent.getPlacedBlockMaterial());
                pp.addClientBlock(clientBlock);
            }
        }

        //update HawkPlayer
        if (event instanceof PositionEvent) {
            pp.setLastMoveTime(System.currentTimeMillis());
            if (event.isCancelled() && ((PositionEvent) event).getCancelLocation() != null) {
                ((PositionEvent) event).setTo(((PositionEvent) event).getCancelLocation());
                pp.setTeleporting(true);
                pp.teleportPlayer(((PositionEvent) event).getCancelLocation(), PlayerTeleportEvent.TeleportCause.PLUGIN);
            } else {
                Location to = ((PositionEvent) event).getTo();
                Location from = ((PositionEvent) event).getFrom();
                pp.setVelocity(new Vector(to.getX() - from.getX(), to.getY() - from.getY(), to.getZ() - from.getZ()));
                pp.setDeltaYaw(to.getYaw() - from.getYaw());
                pp.setDeltaPitch(to.getPitch() - from.getPitch());
                pp.setLocation(to);
                pp.updateFallDistance(to);
                pp.updateTotalAscensionSinceGround(from.getY(), to.getY());
                pp.setOnGround(((PositionEvent) event).isOnGround());
            }

        }
        if (event instanceof AbilitiesEvent && !event.isCancelled() && ((AbilitiesEvent) event).isFlying()) {
            pp.setFlyPendingTime(System.currentTimeMillis());
        }

        return !event.isCancelled();
    }

    public void killListener() {
        packetListener.stop();
    }

    public void setupListenerForOnlinePlayers() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            hawk.getHawkPlayer(p).setOnline(true);
            setupListenerForPlayer(p);
        }
    }

    private void setupListenerForPlayer(Player p) {
        packetListener.start(p);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        setupListenerForPlayer(e.getPlayer());
    }

    public void addAdapterInbound(PacketAdapter runnable) {
        packetListener.addAdapterInbound(runnable);
    }

    public void removeAdapterInbound(PacketAdapter runnable) {
        packetListener.removeAdapterInbound(runnable);
    }

    public void addAdapterOutbound(PacketAdapter runnable) {
        packetListener.addAdapterOutbound(runnable);
    }

    public void removeAdapterOutbound(PacketAdapter runnable) {
        packetListener.removeAdapterOutbound(runnable);
    }
}