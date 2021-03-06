package javabot;

import javabot.model.Channel;
import javabot.model.Logs;
import org.jibble.pircbot.PircBot;

public class MyPircBot extends PircBot {
  private final Javabot javabot;

  public MyPircBot(final Javabot javabot) {
    this.javabot = javabot;
    setVersion(javabot.loadVersion());
    setName(javabot.config.getNick());
    setLogin(javabot.config.getNick());
  }

  public void log(final String string) {
    if (Javabot.log.isInfoEnabled()) {
      Javabot.log.info(string);
    }
  }

  public String getNick() {
    return javabot.nick;
  }

  @Override
  public void onMessage(final String channel, final String sender, final String login, final String hostname,
      final String message) {
    javabot.executors.execute(new Runnable() {
      @Override
      public void run() {
        javabot.processMessage(new IrcEvent(channel, javabot.getUser(sender, login, hostname), message));
      }
    });
  }

  @Override
  public void onJoin(final String channel, final String sender, final String login, final String hostname) {
    javabot.logsDao.logMessage(Logs.Type.JOIN, sender, channel, ":" + hostname + " joined the channel");
  }

  @Override
  public void onPart(final String channel, final String sender, final String login, final String hostname) {
    javabot.logsDao.logMessage(Logs.Type.PART, sender, channel, ":" + hostname + " parted the channel");
  }

  @Override
  public void onQuit(final String channel, final String sender, final String login, final String hostname) {
    javabot.logsDao.logMessage(Logs.Type.QUIT, sender, channel, "quit");
  }

  @Override
  public void onInvite(final String targetNick, final String sourceNick, final String sourceLogin,
      final String sourceHostname, final String channelName) {
    final Channel channel = javabot.channelDao.get(channelName);
    if (channel != null) {
      channel.join(javabot);
    }
  }

  @Override
  public void onDisconnect() {
    System.out.println("MyPircBot.onDisconnect");
    if (!javabot.executors.isShutdown()) {
      try {
        System.out.println("trying to reconnect");
        javabot.setReconnecting(true);
        javabot.connect();
      } catch (Exception e) {
        Javabot.log.error(e.getMessage(), e);
        throw new RuntimeException(e.getMessage(), e);
      }
    }
  }

  @Override
  public void onPrivateMessage(final String sender, final String login, final String hostname,
      final String message) {
    if (javabot.adminDao.isAdmin(sender, hostname) || javabot.isOnSameChannelAs(sender)) {
      javabot.executors.execute(new Runnable() {
        @Override
        public void run() {
          javabot.logsDao.logMessage(Logs.Type.MESSAGE, sender, sender, message);
          for (final Message response : javabot.getResponses(sender, javabot.getUser(sender, login, hostname),
              message)) {
            response.send(javabot);
          }
        }
      });
    }
  }

  @Override
  public void onAction(final String sender, final String login, final String hostname, final String target,
      final String action) {
    javabot.logsDao.logMessage(Logs.Type.ACTION, sender, target, action);
  }

  @Override
  public void onKick(final String channel, final String kickerNick, final String kickerLogin,
      final String kickerHostname, final String recipientNick, final String reason) {
    javabot.logsDao
        .logMessage(Logs.Type.KICK, kickerNick, channel, " kicked " + recipientNick + " (" + reason + ")");
  }
}
