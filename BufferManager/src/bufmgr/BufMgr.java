package bufmgr;

import java.util.LinkedList;
import java.io.IOException;
import diskmgr.*;
import global.*;
import chainexception.ChainException;

public class BufMgr implements GlobalConst {

  private Page[] bufPool;
  private CustomHashTable pageFrame;
  private FrameDesc[] frameDescs;
  private LinkedList<Integer> replaceList;

  /**
   * Create the BufMgr object.
   * Allocate pages (frames) for the buffer pool in main memory and
   * make the buffer manager aware that the replacement policy is
   * specified by replacerArg.
   *
   * @param numbufs number of buffers in the buffer pool.
   * @param replacerArg name of the buffer replacement policy.
   */

  public BufMgr(int numbufs, String replacerArg) {
    bufPool = new Page[numbufs];
    replaceList = new LinkedList<>();
    frameDescs = new FrameDesc[numbufs];
    replacerArg = "FIFO";
    pageFrame = new CustomHashTable(numbufs);

    for (int i = 0; i < numbufs; i++) {
      frameDescs[i] = new FrameDesc();
      bufPool[i] = new Page();
      replaceList.addLast(i);
    }
  }

  /**
   * Pin a page.
   * First check if this page is already in the buffer pool.
   * If it is, increment the pin_count and return a pointer to this
   * page. If the pin_count was 0 before the call, the page was a
   * replacement candidate, but is no longer a candidate.
   * If the page is not in the pool, choose a frame (from the
   * set of replacement candidates) to hold this page, read the
   * page (using the appropriate method from {diskmgr} package) and pin it.
   * Also, must write out the old page in chosen frame if it is dirty
   * before reading new page. (You can assume that emptyPage==false for
   * this assignment.)
   *
   * @param pageno page number in the minibase.
   * @param page the pointer to the page.
   * @param emptyPage true (empty page); false (non-empty page)
   */

  public void pinPage(PageId pageno, Page page, boolean emptyPage) throws ChainException, IOException {
    Integer frameIndex = pageFrame.get(pageno);

    if (frameIndex != null) {
      frameDescs[frameIndex].pinCount++;
      page.setpage(bufPool[frameIndex].getpage());
      if (frameDescs[frameIndex].pinCount == 1) {
        replaceList.remove((Integer) frameIndex);
      }
    } else {
      if (isFull()) {
        try {
          replaceOldPage();
        } catch (BufferPoolExceededException e) {
          throw new BufferPoolExceededException(e, "Buffer Pool Full");
        }
      }

      frameIndex = replaceList.pollFirst();
      if (frameIndex == null) {
        throw new BufferPoolExceededException(new Exception(), "Buffer Pool Full");
      }

      PageId oldPageId = new PageId(frameDescs[frameIndex].pageNumber);
      if (oldPageId.pid != -1 && frameDescs[frameIndex].isDirty) {
        SystemDefs.JavabaseDB.write_page(oldPageId, bufPool[frameIndex]);
        frameDescs[frameIndex].isDirty = false;
      }

      if (oldPageId.pid != -1) {
        pageFrame.remove(oldPageId);
      }

      SystemDefs.JavabaseDB.read_page(pageno, bufPool[frameIndex]);

      page.setpage(bufPool[frameIndex].getpage());

      pageFrame.put(new PageId(pageno.pid), frameIndex);
      frameDescs[frameIndex] = new FrameDesc(pageno.pid, 1, false);
    }
  }

  /**
   * Unpin a page specified by a pageId.
   * This method should be called with dirty==true if the client has
   * modified the page. If so, this call should set the dirty bit
   * for this frame. Further, if pin_count>0, this method should
   * decrement it. If pin_count=0 before this call, throw an exception
   * to report error. (For testing purposes, we ask you to throw
   * an exception named PageUnpinnedException in case of error.)
   *
   * @param pageno page number in the minibase.
   * @param dirty the dirty bit of the frame
   */

  public void unpinPage(PageId pageno, boolean dirty) throws ChainException {
    Integer frameIndex = pageFrame.get(pageno);

    if (frameIndex == null) {
      throw new HashEntryNotFoundException(new Exception(), "Page not found");
    }

    if (frameDescs[frameIndex].pinCount <= 0) {
      throw new PageUnpinnedException(new Exception(), "PageUnpinnedException");
    }

    frameDescs[frameIndex].isDirty |= dirty;
    frameDescs[frameIndex].pinCount--;

    if (frameDescs[frameIndex].pinCount == 0 && !replaceList.contains(frameIndex)) {
      replaceList.addFirst(frameIndex);
    }
  }

  /**
   * Allocate new pages.
   * Call DB object to allocate a run of new pages and
   * find a frame in the buffer pool for the first page
   * and pin it. (This call allows a client of the Buffer Manager
   * to allocate pages on disk.) If buffer is full, i.e., you
   * can't find a frame for the first page, ask DB to deallocate
   * all these pages, and return null.
   *
   * @param firstPage the address of the first page.
   * @param howmany total number of allocated new pages.
   *
   * @return the first page id of the new pages. null, if error.
   */

  public PageId newPage(Page firstPage, int howmany) {
    PageId newPid = new PageId();
    try {
      if (getNumUnpinnedBuffers() < 1) {
        throw new BufferPoolExceededException(new Exception(), "Not enough unpinned buffers available");
      }
      SystemDefs.JavabaseDB.allocate_page(newPid, howmany);
      pinPage(newPid, firstPage, false);
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
    return newPid;
  }

  /**
   * This method should be called to delete a page that is on disk.
   * This routine must call the method in diskmgr package to
   * deallocate the page.
   *
   * @param globalPageId the page number in the data base.
   */

  public void freePage(PageId globalPageId) throws ChainException, IOException {
    Integer frameIndex = pageFrame.get(globalPageId);

    if (frameIndex == null) {
      try {
        SystemDefs.JavabaseDB.deallocate_page(globalPageId);
      } catch (ChainException | IOException e) {
        e.printStackTrace();
        throw new ChainException(e, "Error while deallocating the page");
      }
      return;
    }

    if (frameDescs[frameIndex].pinCount > 1) {
      throw new PagePinnedException(new Exception(), "Page still pinned");
    }

    if (frameDescs[frameIndex].pinCount == 1) {
      unpinPage(globalPageId, frameDescs[frameIndex].isDirty);
    }

    if (frameDescs[frameIndex].isDirty) {
      flushPage(globalPageId);
    }

    pageFrame.remove(globalPageId);
    bufPool[frameIndex] = new Page();
    frameDescs[frameIndex] = new FrameDesc();
    replaceList.add((Integer) frameIndex);

    try {
      SystemDefs.JavabaseDB.deallocate_page(globalPageId);
    } catch (ChainException | IOException e) {
      e.printStackTrace();
      throw new ChainException(e, "Error while deallocating the page");
    }
  }

  /**
   * Used to flush a particular page of the buffer pool to disk.
   * This method calls the write_page method of the diskmgr package.
   *
   * @param pageid the page number in the database.
   */


  public void flushPage(PageId pageid) {
    Integer frameIndex = pageFrame.get(pageid);
    if (frameIndex != null) {
      try {
        SystemDefs.JavabaseDB.write_page(pageid, bufPool[frameIndex]);
        frameDescs[frameIndex].isDirty = false;
      } catch (Exception e) {
        System.err.println(e);
      }
    }
  }

  /** Flushes all pages of the buffer pool to disk */

  public void flushAllPages() {
    for (int i = 0; i < frameDescs.length; i++) {
      if (frameDescs[i].isDirty) {
        flushPage(new PageId(frameDescs[i].pageNumber));
      }
    }
  }

  /** Gets the total number of buffers.
   *
   * @return total number of buffer frames.
   */
  public int getNumBuffers() {
    return bufPool.length;
  }

  /** Gets the total number of unpinned buffer frames.
   *
   * @return total number of unpinned buffer frames.
   */
  public int getNumUnpinnedBuffers() {
    int count = 0;
    for (FrameDesc description : frameDescs) {
      if (description.pinCount == 0) {
        count++;
      }
    }
    return count;
  }

  private boolean isFull() {
    return replaceList.isEmpty();
  }

  private void replaceOldPage() throws ChainException, IOException {
    if (replaceList.isEmpty()) {
      throw new BufferPoolExceededException(new Exception(), "Buffer pool exceeded!");
    }

    int frameToReplace = replaceList.removeFirst();
    PageId pageId = new PageId(frameDescs[frameToReplace].pageNumber);

    if (frameDescs[frameToReplace].isDirty) {
      flushPage(pageId);
    }

    pageFrame.remove(pageId);
    frameDescs[frameToReplace] = new FrameDesc();
    bufPool[frameToReplace] = new Page();
  }
}
