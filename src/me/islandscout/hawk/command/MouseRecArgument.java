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

package me.islandscout.hawk.command;

import me.islandscout.hawk.module.MouseRecorder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MouseRecArgument extends Argument {

    public MouseRecArgument() {
        super("mouserec", "<player> [seconds]", "Record a player's mouse movements.");
    }

    @Override
    public boolean process(CommandSender sender, Command cmd, String label, String[] args) {
        if(args.length < 2)
            return false;
        Player target = Bukkit.getPlayer(args[1]);
        if(target == null) {
            sender.sendMessage(ChatColor.RED + "Unknown player \"" + args[1] + "\"");
            return true;
        }

        float time = 0;
        if(args.length == 3) {
            try {
                time = Float.parseFloat(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Third argument must be a non-negative real number.");
            }
            if(time < 0)
                sender.sendMessage(ChatColor.RED + "Third argument must be a non-negative real number.");
        }

        hawk.getMouseRecorder().start(sender, target, time);

        return true;
    }
}