package com.xly.codeforge.judge.codesandbox;

import com.xly.codeforge.judge.codesandbox.impl.ExampleCodeSandbox;
import com.xly.codeforge.judge.codesandbox.impl.RemoteCodeSandbox;

public class CodeSandboxFactory {

    public static CodeSandbox newInstance(String type) {
        switch (type) {
            case "remote":
                return new RemoteCodeSandbox();
            case "example":
            default:
                return new ExampleCodeSandbox();
        }
    }
}
