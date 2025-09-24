package org.tahoma.rwreports;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class RWMySQLOnline implements RWMain.OnlineStatusProvider {

    private boolean connectedToAPI = false;
    private RWMain rwMain;

    public RWMySQLOnline(RWMain rwMain) {
        this.rwMain = rwMain;
    }

    @Override
    public void connectToAPI() {
        connectedToAPI = true;
    }

    @Override
    public void disconnectFromAPI() {
        connectedToAPI = false;
    }

    @Override
    public boolean isOnline(String playerName) {
        return false;
    }

    @Override
    public Map<String, Boolean> getOnlineStatus(Collection<String> playerNames) {
        if (!connectedToAPI) {
            System.out.println("Данные об онлайне недоступны, MySQL не отслеживает статус.");
            return Collections.emptyMap();
        }
        Map<String, Boolean> result = new HashMap<>();
        for (String name : playerNames) {
            result.put(name, false);
        }
        return result;
    }

    @Override
    public Map<String, String> getServerNames(Collection<String> playerNames) {
        if (!connectedToAPI) {
            System.out.println("Данные о серверах недоступны, MySQL не отслеживает статус.");
            return Collections.emptyMap();
        }
        Map<String, String> result = new HashMap<>();
        for (String name : playerNames) {
            result.put(name, "-");
        }
        return result;
    }
}