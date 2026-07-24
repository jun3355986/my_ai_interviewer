package com.aiinterviewer.interview.model;

public interface TurnModelClient {

    TurnModelResult process(TurnModelCommand command) throws Exception;
}
