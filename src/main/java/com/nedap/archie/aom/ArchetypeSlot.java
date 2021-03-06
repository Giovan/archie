package com.nedap.archie.aom;

import com.nedap.archie.rules.Assertion;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by pieter.bos on 15/10/15.
 */
@XmlType(name="ARCHETYPE_SLOT", propOrder= {
        "includes",
        "excludes",
        "closed"
})
public class ArchetypeSlot extends CObject {

    private List<Assertion> includes = new ArrayList<>();
    private List<Assertion> excludes = new ArrayList<>();
    @XmlElement(name="is_closed")
    private boolean closed = false;

    public List<Assertion> getIncludes() {
        return includes;
    }

    public void setIncludes(List<Assertion> includes) {
        this.includes = includes;
    }

    public List<Assertion> getExcludes() {
        return excludes;
    }

    public void setExcludes(List<Assertion> excludes) {
        this.excludes = excludes;
    }

    public boolean isClosed() {
        return closed;
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }
}
