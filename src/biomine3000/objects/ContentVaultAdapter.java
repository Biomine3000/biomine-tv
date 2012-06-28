package biomine3000.objects;


import util.ExceptionUtils;
import util.dbg.Logger;

import biomine3000.objects.ContentVaultProxy;
import biomine3000.objects.ContentVaultProxy.ContentVaultListener;
import biomine3000.objects.ContentVaultProxy.InvalidStateException;


/**
 * Adapts an arbitraty {@link IBusinessObjectHandler} to receive periodic
 * updates from an content vault.
 */
public class ContentVaultAdapter {

    private static boolean log = false;

    private IBusinessObjectHandler handler;
    private boolean firstImageLoaded;
    private ContentVaultProxy contentVaultProxy;
    private ContentListener contentListener;
    private Sender sender;
    /**
     * Time between sends (in millis)
     */
    private int sendInterval;

    /**
     * Creates a content vault and starts listening to it. Caller needs to call startLoading
     * to instruct content vault to start loading content; this shall later trigger
     * a sender thread to actually push content to the handler periodically, as dictated by
     * sendInterval.
     *
     * @param sendInterval interval between sent objects (in milliseconds).
     */
    public ContentVaultAdapter(IBusinessObjectHandler handler, int sendInterval) {
        this.handler = handler;
        this.sendInterval = sendInterval;
        // init communications with the server
        firstImageLoaded = false;
        contentVaultProxy = new ContentVaultProxy();
        contentListener = new ContentListener();
        contentVaultProxy.addListener(contentListener);
    }

    public void startLoading() {
        contentVaultProxy.startLoading();
    }

    /**
     * Stop your business (instruct sender thread to stop sending content).
     */
    public void stop() {
        log("stop requested");
        sender.stop();
    }

    /**
     * Sends event text as a PlainText "service/state-changed" event
     */
    private void sendEvent(String msg) {
        log("Sending message: " + msg);
        PlainTextObject obj = new PlainTextObject(msg);
        obj.getMetaData().setEvent(BusinessObjectEventType.SERVICES_STATE_CHANGED);
        handler.handleObject(obj);
    }

    private class ContentListener implements ContentVaultListener {

        @Override
        public void loadedImageList() {
            String msg = "Loaded urls for all " + contentVaultProxy.getNumLoadedObjects() + " business objects";
            sendEvent(msg);
        }

        @Override
        public void loadedImage(String image) {
            String msg = "Loaded " + contentVaultProxy.getNumLoadedObjects() + "/" + contentVaultProxy.getTotalNumObjects() + " business objects";
            sendEvent(msg);
            if (firstImageLoaded == false) {
                log("First image loaded, starting sender thread to push content to handler");
                firstImageLoaded = true;
                sender = new Sender();
                new Thread(sender).start();
            }
        }

        @Override
        public void loadedAllImages() {
            log("All images have been loaded");

        }
    }

    /**
     * Sends random content from vault every 3 seconds to handler
     */
    private class Sender implements Runnable {
        private boolean stop = false;

        private void stop() {
            log("stop requested");
            stop = true;
        }

        public void run() {
            log("Sender running");

            while (!stop) {
                try {
                    ImageObject randomContent = contentVaultProxy.sampleImage();
                    handler.handleObject(randomContent);
                    Thread.sleep(sendInterval);
                } catch (InvalidStateException e) {
                    handler.handleObject(new ErrorObject(ExceptionUtils.format(e, "; ")));
                    try {
                        Thread.sleep(sendInterval);
                    } catch (InterruptedException ie) {
                        // no action
                    }
                } catch (InterruptedException e) {
                    // no action
                }
            }

            log("stopped");
        }

        private void log(String msg) {
            if (log) Logger.info("ContentVaultAdapter.Sender: " + msg);
        }
    }

    /**
     * Unit testing
     */
    public static void main(String[] args) {
        ContentVaultAdapter adapter = new ContentVaultAdapter(
                new IBusinessObjectHandler() {
                    @Override
                    public void handleObject(BusinessObject bo) {
                        log("DUMMY HANDLER received object: " + bo);
                    }
                }, 3000);
        adapter.startLoading();
    }

    private static void log(String msg) {
        if (log) Logger.info("ContentVaultAdapter: " + msg);
    }


}
