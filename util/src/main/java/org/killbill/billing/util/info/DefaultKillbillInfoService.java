/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.billing.util.info;

import java.util.List;

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.killbill.CreatorName;
import org.killbill.billing.osgi.api.PluginInfo;
import org.killbill.billing.osgi.api.PluginsInfoApi;
import org.killbill.billing.platform.api.LifecycleHandlerType;
import org.killbill.billing.platform.api.LifecycleHandlerType.LifecycleLevel;
import org.killbill.billing.util.info.dao.NodeInfoDao;
import org.killbill.billing.util.info.dao.NodeInfoModelDao;
import org.killbill.billing.util.info.json.NodeInfoModelJson;
import org.killbill.billing.util.info.json.PluginInfoModelJson;
import org.killbill.clock.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class DefaultKillbillInfoService implements KillbillInfoService {

    private static final Logger logger = LoggerFactory.getLogger(DefaultKillbillInfoService.class);

    public static final String INFO_SERVICE_NAME = "info-service";

    private final NodeInfoDao nodeInfoDao;
    private final PluginsInfoApi pluginInfoApi;
    private final Clock clock;
    private final NodeInfoMapper mapper;

    @Inject
    public DefaultKillbillInfoService(final NodeInfoDao nodeInfoDao, final PluginsInfoApi pluginInfoApi, final Clock clock, final NodeInfoMapper mapper) {
        this.nodeInfoDao = nodeInfoDao;
        this.pluginInfoApi = pluginInfoApi;
        this.clock = clock;
        this.mapper = mapper;
    }

    @Override
    public String getName() {
        return INFO_SERVICE_NAME;
    }

    @LifecycleHandlerType(LifecycleHandlerType.LifecycleLevel.START_SERVICE)
    public void start() {
        try {
            createBootNodeInfo();
        } catch (JsonProcessingException e) {
            logger.error("Failed to create bootNodeInfo", e);
        }
    }

    @LifecycleHandlerType(LifecycleLevel.STOP_SERVICE)
    public void stop() {
        nodeInfoDao.delete(CreatorName.get());
    }

    private void createBootNodeInfo() throws JsonProcessingException {

        final DateTime bootTime = clock.getUTCNow();
        final Iterable<PluginInfo> rawPluginInfo = pluginInfoApi.getPluginsInfo();
        final List<PluginInfo> pluginInfo = rawPluginInfo.iterator().hasNext() ? ImmutableList.<PluginInfo>copyOf(rawPluginInfo) : ImmutableList.<PluginInfo>of();
        final String kbVersion = org.killbill.billing.util.info.KillbillVersions.getKillbillVersion();
        final String kbApiVersion  = org.killbill.billing.util.info.KillbillVersions.getApiVersion();
        final String kbPluginApiVersion  = org.killbill.billing.util.info.KillbillVersions.getPluginApiVersion();
        final String kbPlatformVersion  = org.killbill.billing.util.info.KillbillVersions.getPlatformVersion();
        final String kbCommonVersion  = org.killbill.billing.util.info.KillbillVersions.getCommonVersion();


        final NodeInfoModelJson nodeInfo = new NodeInfoModelJson(CreatorName.get(), bootTime, bootTime, kbVersion, kbApiVersion, kbPluginApiVersion, kbCommonVersion, kbPlatformVersion,
             ImmutableList.copyOf(Iterables.transform(pluginInfo, new Function<PluginInfo, PluginInfoModelJson>() {
                 @Override
                 public PluginInfoModelJson apply(final PluginInfo input) {
                     return new PluginInfoModelJson(input);
                 }
             })));

        final String nodeInfoValue = mapper.serialize(nodeInfo);
        final NodeInfoModelDao bootNodeInfo = new NodeInfoModelDao(CreatorName.get(), clock.getUTCNow(), nodeInfoValue);
        nodeInfoDao.create(bootNodeInfo);
    }
}
