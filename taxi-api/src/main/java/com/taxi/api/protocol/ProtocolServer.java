package com.taxi.api.protocol;

import com.taxi.application.service.Protocol808AppService;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * JT/T 808 接入服务（Netty）。
 * <p>默认端口 7611，可通过 {@code protocol.port} 调整；{@code protocol.enabled=false} 可整体关闭
 * （本地只调 REST 时使用）。与 REST 端口 8080 分离，便于设备侧做端口映射。</p>
 */
@Component
@ConditionalOnProperty(name = "protocol.enabled", havingValue = "true", matchIfMissing = true)
public class ProtocolServer {

    private static final Logger log = LoggerFactory.getLogger(ProtocolServer.class);

    private final Protocol808AppService appService;
    private final TerminalSessionRegistry sessionRegistry;

    @Value("${protocol.port:7611}")
    private int port;

    @Value("${protocol.worker-threads:0}")
    private int workerThreads;

    @Value("${protocol.idle-seconds:180}")
    private int idleSeconds;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public ProtocolServer(Protocol808AppService appService, TerminalSessionRegistry sessionRegistry) {
        this.appService = appService;
        this.sessionRegistry = sessionRegistry;
    }

    @PostConstruct
    public void start() {
        int workers = workerThreads > 0 ? workerThreads : Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(workers);
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 256)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast(new IdleStateHandler(idleSeconds, 0, 0, TimeUnit.SECONDS))
                                    .addLast(new Jt808FrameDecoder())
                                    .addLast(new Jt808ServerHandler(appService, sessionRegistry));
                        }
                    });
            serverChannel = bootstrap.bind(port).sync().channel();
            log.info("JT/T 808 接入服务已启动 port={} workerThreads={} idleSeconds={}",
                    port, workers, idleSeconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            shutdownGroups();
            throw new IllegalStateException("808 接入服务启动被中断", e);
        } catch (RuntimeException e) {
            shutdownGroups();
            throw e;
        }
    }

    @PreDestroy
    public void stop() {
        if (serverChannel != null) {
            serverChannel.close();
        }
        shutdownGroups();
        log.info("JT/T 808 接入服务已停止 port={}", port);
    }

    private void shutdownGroups() {
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
    }

    /** 当前在线终端数，供健康检查/运维查看 */
    public int onlineTerminals() {
        return sessionRegistry.onlineCount();
    }

    public int getPort() {
        return port;
    }
}
