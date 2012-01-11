package io.netty.build.checkstyle;

import java.util.regex.Pattern;

import com.puppycrawl.tools.checkstyle.api.AuditEvent;
import com.puppycrawl.tools.checkstyle.api.AutomaticBean;
import com.puppycrawl.tools.checkstyle.api.Filter;
import com.puppycrawl.tools.checkstyle.api.FilterSet;

public class SuppressionFilter extends AutomaticBean implements Filter {

    private FilterSet filters = new FilterSet();
    private Pattern pattern;
    
    public void setPattern(String pattern) {
        this.pattern = Pattern.compile(pattern);
    }

    @Override
    public boolean accept(AuditEvent evt) {
        return !pattern.matcher(evt.getFileName()).find();
    }
}
