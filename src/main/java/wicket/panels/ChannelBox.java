package wicket.panels;

import javabot.dao.LogDao;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.RepeatingView;
import org.apache.wicket.spring.injection.annot.SpringBean;
import wicket.pages.Index;

import java.util.List;

//
// Author: joed

// Date  : May 18, 2007
public class ChannelBox extends Panel {

    @SpringBean
    private LogDao l_dao;

    public ChannelBox(String id) {
        super(id);

        List<String> channels = l_dao.loggedChannels();

        RepeatingView repeating = new RepeatingView("logged_channels");
        add(repeating);

        if (channels.size() > 0) {

            for (String channel : channels) {
                WebMarkupContainer item = new WebMarkupContainer(repeating.newChildId());
                repeating.add(item);
                Link link = new BookmarkablePageLink("link", Index.class).setParameter("channel", channel);
                link.add(new Label("channel", channel));
                item.add(link);
            }

        } else {
            WebMarkupContainer item = new WebMarkupContainer(repeating.newChildId());
            repeating.add(item);
            Link link = new BookmarkablePageLink("link", Index.class);
            link.add(new Label("channel", "Nothing logged...."));
            item.add(link);
            item.add(link);
        }
    }

}