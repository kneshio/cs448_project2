package bufmgr;

import global.PageId;
public class FrameDesc {
    public int pageNumber;
    public int pinCount;
    public boolean isDirty;

    public FrameDesc() {
        pageNumber = new PageId().pid;
        pinCount = 0;
        isDirty = false;
    }

    //Test Constructor
    public FrameDesc(int pageNumber, int pinCount, boolean isDirty) {
        this.pageNumber = pageNumber;
        this.pinCount = pinCount;
        this.isDirty = isDirty;
    }

}
