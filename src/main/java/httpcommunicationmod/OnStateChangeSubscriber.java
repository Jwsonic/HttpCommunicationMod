package httpcommunicationmod;

import basemod.interfaces.ISubscriber;

public interface OnStateChangeSubscriber extends ISubscriber {
    void receiveOnStateChange();
}
