package com.assistent.api;

import com.assistent.api.user.AppUser;
import com.assistent.api.workspace.Workspace;
import java.util.UUID;

public final class TestData {

    public static final UUID WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    public static final UUID OTHER_WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    public static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    private TestData() {
    }

    public static Workspace workspace(UUID id) {
        Workspace workspace = Workspace.create("Acme");
        workspace.setId(id);
        workspace.setSystemPrompt("Seja cordial.");
        return workspace;
    }

    public static AppUser user(UUID id, Workspace workspace) {
        AppUser user = AppUser.create(workspace, "Ana", "ana@acme.com", "{bcrypt}hash");
        user.setId(id);
        return user;
    }

}
