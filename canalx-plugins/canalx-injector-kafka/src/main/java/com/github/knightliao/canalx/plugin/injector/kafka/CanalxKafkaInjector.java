package com.github.knightliao.canalx.plugin.injector.kafka;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.knightliao.canalx.core.exception.CanalxInjectorException;
import com.github.knightliao.canalx.core.exception.CanalxInjectorInitException;
import com.github.knightliao.canalx.core.plugin.injector.ICanalxInjector;
import com.github.knightliao.canalx.core.plugin.injector.IInjectorEntryProcessorAware;
import com.github.knightliao.canalx.core.plugin.injector.template.InjectorEventProcessTemplate;
import com.github.knightliao.canalx.core.support.annotation.PluginName;
import com.github.knightliao.canalx.core.support.context.ICanalxContext;
import com.github.knightliao.canalx.core.support.context.ICanalxContextAware;
import com.github.knightliao.canalx.plugin.injector.kafka.support.InjectorSupport;
import com.github.knightliao.canalx.plugin.injector.kafka.support.config.InjectorKafkaConfig;

import kafka.consumer.ConsumerIterator;
import kafka.consumer.KafkaStream;

/**
 * @author knightliao
 * @date 2016/11/23 18:21
 */
@PluginName(name = "canalx-injector-kafka")
public class CanalxKafkaInjector implements ICanalxInjector, IInjectorEntryProcessorAware, ICanalxContextAware {

    protected static final Logger LOGGER = LoggerFactory.getLogger(CanalxKafkaInjector.class);

    // config
    private InjectorKafkaConfig injectorKafkaConfig = new InjectorKafkaConfig();

    // process entry template
    private InjectorEventProcessTemplate injectorEventProcessTemplate;

    // thread executors
    private ExecutorService executor = null;

    //
    private ICanalxContext iCanalxContext = null;

    @Override
    public void init() throws CanalxInjectorInitException {

        try {
            injectorKafkaConfig.init(iCanalxContext);
        } catch (Exception e) {
            throw new CanalxInjectorInitException(e);
        }
    }

    @Override
    public void run() throws CanalxInjectorException {

        if (injectorKafkaConfig.getConsumer() != null) {

            // set topic map
            Map<String, Integer> topicCountMap = new HashMap<String, Integer>();

            for (String topic : injectorKafkaConfig.getTopicList()) {
                topicCountMap.put(topic, 1);

            }

            // get stream
            Map<String, List<KafkaStream<byte[], byte[]>>> consumerMap =
                    injectorKafkaConfig.getConsumer().createMessageStreams
                            (topicCountMap);

            // executors
            executor = Executors.newFixedThreadPool(consumerMap.size());
            for (String topicStr : consumerMap.keySet()) {

                List<KafkaStream<byte[], byte[]>> newOrderStreams = consumerMap.get(topicStr);

                ConsumerIterator<byte[], byte[]> it = newOrderStreams.get(0).iterator();

                executor.submit(new InjectorSupport(it, topicStr, injectorEventProcessTemplate));
                LOGGER.info("submit thread-{} ", topicStr);
            }
        }
    }

    @Override
    public void shutdown() throws CanalxInjectorException {

        if (executor != null) {
            executor.shutdown();

            try {
                if (!executor.awaitTermination(5000, TimeUnit.MILLISECONDS)) {
                    LOGGER.info("Timed out waiting for consumer threads (topic: {} ) to shut down, exiting uncleanly",
                            injectorKafkaConfig.getTopicList());
                }
            } catch (InterruptedException e) {
                LOGGER.warn("Interrupted during shutdown, exiting uncleanly");
            }

            // shut down callback
            if (injectorEventProcessTemplate != null) {

                injectorEventProcessTemplate.shutdown();
            }
        }

        LOGGER.info(CanalxKafkaInjector.class.toString() + " stops gracefully");
    }

    @Override
    public void setupProcessEntry(InjectorEventProcessTemplate injectorEventProcessTemplate) {
        this.injectorEventProcessTemplate = injectorEventProcessTemplate;
    }

    @Override
    public void setCanalxContext(ICanalxContext iCanalxContext) {
        this.iCanalxContext = iCanalxContext;
    }
}
