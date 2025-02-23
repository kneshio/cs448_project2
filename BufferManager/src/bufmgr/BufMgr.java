package bufmgr;

import java.util.LinkedList;
import java.io.IOException;
import diskmgr.*;
import global.*;
import chainexception.ChainException;

public class BufMgr implements GlobalConst {


  private Page[] bufPool;
  private CustomPageHashMap pageFrame;
  private FrameDesc[] descriptions;
  private LinkedList<Integer> replaceList;

  /**
   * Create the BufMgr object.
   * Allocate pages (frames) for the buffer pool in main memory and
   * make the buffer manage aware that the replacement policy is
   * specified by replacerArg.
   *
   * @param numbufs number of buffers in the buffer pool.
   * @param replacerArg name of the buffer replacement policy.
   */

  public BufMgr(int numbufs, String replacerArg) {
    bufPool = new Page[numbufs];
    replaceList = new LinkedList<>();
    descriptions = new FrameDesc[numbufs];
    replacerArg = "FIFO";
    pageFrame = new CustomPageHashMap(numbufs);

    for (int i = 0; i < numbufs; i++) {
      descriptions[i] = new FrameDesc();
      bufPool[i] = new Page();
      replaceList.addLast(i);
    }
  }

  /**
   * Pin a page.
   * First check if this page is already in the buffer pool.
   * If it is, increment the pin_count and return a pointer to this
   * page.  If the pin_count was 0 before the call, the page was a
   * replacement candidate, but is no longer a candidate.
   * If the page is not in the pool, choose a frame (from the
   * set of replacement candidates) to hold this page, read the
   * page (using the appropriate method from {diskmgr} package) and pin it.
   * Also, must write out the old page in chosen frame if it is dirty
   * before reading new page.  (You can assume that emptyPage==false for
   * this assignment.)
   *
   * @param Page_Id_in_a_DB page number in the minibase.
   * @param page the pointer poit to the page.
   * @param emptyPage true (empty page); false (non-empty page)
   */

  public void pinPage(PageId pageno, Page page, boolean emptyPage) throws ChainException, IOException {
    Integer frameIndex = pageFrame.get(pageno);

    if (frameIndex != null) {
      // Page already in buffer
      descriptions[frameIndex].pinCount++;
      page.setpage(bufPool[frameIndex].getpage());
    } else {
      // Check for available frame
      if (isFull()) {
        replaceOldPage();
      }

      frameIndex = replaceList.pollFirst();
      if (frameIndex == null) {
        throw new BufferPoolExceededException(new Exception(), "Buffer Full");
      }

      PageId oldPageId = new PageId(descriptions[frameIndex].pageNumber);
      if (descriptions[frameIndex].isDirty) {
        SystemDefs.JavabaseDB.write_page(oldPageId, bufPool[frameIndex]);
      }

      pageFrame.remove(oldPageId);
      SystemDefs.JavabaseDB.read_page(pageno, page);
      pageFrame.put(new PageId(pageno.pid), frameIndex);
      bufPool[frameIndex].setpage(page.getpage());
      descriptions[frameIndex] = new FrameDesc(pageno.pid, 1, false);
    }
  }

  /**
   * Unpin a page specified by a pageId.
   * This method should be called with dirty==true if the client has
   * modified the page.  If so, this call should set the dirty bit
   * for this frame.  Further, if pin_count>0, this method should
   * decrement it. If pin_count=0 before this call, throw an exception
   * to report error.  (For testing purposes, we ask you to throw
   * an exception named PageUnpinnedException in case of error.)
   *
   * @param globalPageId_in_a_DB page number in the minibase.
   * @param dirty the dirty bit of the frame
   */

  public void unpinPage(PageId pageno, boolean dirty) throws ChainException {
    Integer frameIndex = pageFrame.get(pageno);
    if (frameIndex == null) {
      throw new HashEntryNotFoundException(new Exception(), "Page not found");
    }

    if (descriptions[frameIndex].pinCount <= 0) {
      throw new ChainException(new Exception(), "PageUnpinnedException");
    }

    descriptions[frameIndex].isDirty |= dirty; // Mark as dirty if specified
    descriptions[frameIndex].pinCount--;

    if (descriptions[frameIndex].pinCount == 0 && !replaceList.contains(frameIndex)) {
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
   * @param firstpage the address of the first page.
   * @param howmany total number of allocated new pages.
   *
   * @return the first page id of the new pages.  null, if error.
   */

  public PageId newPage(Page firstPage, int howmany) {
    PageId newPid = new PageId();
    try {
      // Check if there are enough unpinned buffers before allocating pages
      if (getNumUnpinnedBuffers() < 1) {
        throw new BufferPoolExceededException(new Exception(), "Not enough unpinned buffers available");
      }

      // Try to allocate the requested number of pages
      SystemDefs.JavabaseDB.allocate_page(newPid, howmany);
      // Pin the first page of the new allocation
      pinPage(newPid, firstPage, false);
    } catch (OutOfSpaceException e) {
      e.printStackTrace();
      // Handle out of space condition
    } catch (IOException | ChainException e) {
      e.printStackTrace();
    }

    // Return the page ID of the newly allocated pages, or null if there was an error
    return newPid;
  }

  /**
   * This method should be called to delete a page that is on disk.
   * This routine must call the method in diskmgr package to
   * deallocate the page.
   *
   * @param globalPageId the page number in the data base.
   */

  public void freePage(PageId globalPageId) throws ChainException {
    Integer frameIndex = pageFrame.get(globalPageId);

    // If the page is not found in the buffer pool, simply return
    if (frameIndex == null) {
        return; // Page not found, no need to do anything
    }

    // // Check if the page is pinned before freeing it
    // if (descriptions[frameIndex].pinCount > 0) {
    //     // Throw exception if the page is still pinned
    //     throw new PagePinnedException(new Exception(), "Page still pinned");
    // }

    // Log the state before deallocating the page
    System.out.println("Freeing page: " + globalPageId + " at frame index: " + frameIndex);

    // Free the page
    try {
        SystemDefs.JavabaseDB.deallocate_page(globalPageId);
    } catch (ChainException | IOException e) {
        e.printStackTrace();
        throw new ChainException(e, "Error while deallocating the page");
    }

    // Reset buffer description and remove page from hash map
    descriptions[frameIndex] = new FrameDesc();
    pageFrame.remove(globalPageId);

    // Check if the frame index is in the replace list before removing
    if (replaceList.contains(frameIndex)) {
        replaceList.remove(frameIndex);
    } else {
        System.err.println("Warning: Frame index " + frameIndex + " not found in replaceList");
    }

    // Log the state after deallocating the page
    System.out.println("Page " + globalPageId + " freed from frame index: " + frameIndex);
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
        descriptions[frameIndex].isDirty = false; // Mark page as clean after flushing
      } catch (Exception e) {
        System.err.println(e);
      }
    }
  }

  /** Flushes all pages of the buffer pool to disk
   */

  public void flushAllPages() {
    for (int i = 0; i < descriptions.length; i++) {
      if (descriptions[i].isDirty) {
        flushPage(new PageId(descriptions[i].pageNumber));
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
    for (FrameDesc description : descriptions) {
      if (description.pinCount == 0) count++;
    }
    return count;
  }

  private boolean isFull() {
    return getNumUnpinnedBuffers() == 0;
  }

  private void replaceOldPage() throws ChainException {
    for (int i = 0; i < bufPool.length; i++) {
      if (descriptions[i].pinCount == 0) {
        PageId pageId = new PageId(descriptions[i].pageNumber);
        freePage(pageId);
        return;
      }
    }
    throw new BufferPoolExceededException(new Exception(), "No unpinned pages available");
  }
}


