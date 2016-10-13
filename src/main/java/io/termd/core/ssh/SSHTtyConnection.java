package io.termd.core.ssh;

import io.termd.core.tty.TtyConnection;
import org.apache.sshd.server.ChannelSessionAware;
import org.apache.sshd.server.channel.ChannelSession;

/**
 * Created by g on 10/13/16.
 */
public interface SSHTtyConnection extends TtyConnection , ChannelSessionAware{
    ChannelSession getSession();
}
