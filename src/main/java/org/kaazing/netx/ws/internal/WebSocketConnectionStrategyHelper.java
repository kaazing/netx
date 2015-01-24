package org.kaazing.netx.ws.internal;

import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import org.kaazing.netx.ws.spi.WebSocketConnectionStrategySpi;


public final class WebSocketConnectionStrategyHelper {
    private static final Map<String, List<WebSocketConnectionStrategySpi>> _strategies;

    static {
        Class<WebSocketConnectionStrategySpi> clazz = WebSocketConnectionStrategySpi.class;
        ServiceLoader<WebSocketConnectionStrategySpi> loader = ServiceLoader.load(clazz);
        Map<String, List<WebSocketConnectionStrategySpi>> strategiesMap
                           = new HashMap<String, List<WebSocketConnectionStrategySpi>>();

        for (WebSocketConnectionStrategySpi strategySpi: loader) {
            Collection<String> strategies = strategySpi.getSupportedStrategies();
            boolean wsn = false;

            if (strategies.contains("wsn")) {
                wsn = true;
            }

            for (String strategy : strategies)
            {
                List<WebSocketConnectionStrategySpi> list = strategiesMap.get(strategy);
                if (list == null) {
                    list = new ArrayList<WebSocketConnectionStrategySpi>();

                    // If one of the strategies is "wsn", then we should ensure that
                    // it is first one to be used.
                    if (wsn) {
                        list.add(0, strategySpi);
                    }
                    else {
                        list.add(strategySpi);
                    }
                }
                strategiesMap.put(strategy, list);
            }
        }
        _strategies = unmodifiableMap(strategiesMap);
    }

    private WebSocketConnectionStrategyHelper() {
    }

    public static WebSocketConnectionStrategyHelper newInstance() {
        return new WebSocketConnectionStrategyHelper();
    }

    public List<WebSocketConnectionStrategySpi> getConnectionStrategies(String strategyName) {
        return unmodifiableList(_strategies.get(strategyName));
    }
}
