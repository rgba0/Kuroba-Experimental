/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.core.repository;

import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.sites.chan4.Chan4;

import java.util.HashMap;
import java.util.Map;

public class LastReplyRepository {
    private Map<SiteBoard, Long> lastReplyMap = new HashMap<>();
    private Map<SiteBoard, Long> lastThreadMap = new HashMap<>();

    public void putLastReply(Site s, Board b) {
        lastReplyMap.put(new SiteBoard(s, b), System.currentTimeMillis());
    }

    public long getLastReply(Site s, Board b) {
        Long lastTime = lastReplyMap.get(new SiteBoard(s, b));
        return lastTime != null ? lastTime : 0L;
    }

    public void putLastThread(Site s, Board b) {
        lastThreadMap.put(new SiteBoard(s, b), System.currentTimeMillis());
    }

    public long getLastThread(Site s, Board b) {
        Long lastTime = lastThreadMap.get(new SiteBoard(s, b));
        return lastTime != null ? lastTime : 0L;
    }

    public boolean canPostReply(Site s, Board b, boolean hasImage) {
        boolean half = s instanceof Chan4 && s.actions().isLoggedIn();
        int cooldownTime = (hasImage ? b.cooldownImages : b.cooldownReplies) * (half ? 500 : 1000);
        return getLastReply(s, b) + cooldownTime < System.currentTimeMillis();
    }

    public boolean canPostThread(Site s, Board b) {
        boolean half = s instanceof Chan4 && s.actions().isLoggedIn();
        return getLastThread(s, b) + b.cooldownThreads * (half ? 500 : 1000) < System.currentTimeMillis();
    }

    private class SiteBoard {
        public String site;
        public String boardCode;

        public SiteBoard(Site site, Board board) {
            this.site = site.name();
            this.boardCode = board.code;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof SiteBoard)) return false;
            SiteBoard sb = (SiteBoard) o;
            return sb.boardCode.equals(this.boardCode) && sb.site.equals(this.site);
        }

        @Override
        public int hashCode() {
            return (site + boardCode).hashCode();
        }
    }
}
