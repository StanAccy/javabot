package javabot.commands;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

import com.antwerkz.maven.SPI;
import javabot.IrcEvent;
import javabot.Javabot;
import javabot.Message;
import javabot.dao.ApiDao;
import javabot.javadoc.JavadocApi;

/**
 * Created Jan 9, 2009
 *
 * @author <a href="mailto:jlee@antwerkz.com">Justin Lee</a>
 */
@SPI({AdminCommand.class})
public class InfoApi extends AdminCommand {
  @Inject
  private ApiDao dao;
  @Param
  String name;

  @Override
  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  public List<Message> execute(final Javabot bot, final IrcEvent event) {
    final List<Message> responses = new ArrayList<Message>();
    final String destination = event.getChannel();
    final JavadocApi api = dao.find(name);
    if (api != null) {
      responses.add(new Message(destination, event, String.format(
          "The %s API can be found at %s.  I know about these packages from that API:  %s",
          api.getName(), api.getBaseUrl(), api.getPackages() == null ? "*" : api.getPackages())));
    } else {
      responses.add(new Message(destination, event, String.format(
          "I don't have javadoc for %s, %s", name, event.getSender())));
    }
    return responses;
  }
}