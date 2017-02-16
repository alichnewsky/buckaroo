package com.loopperfect.buckaroo.cli;

import com.loopperfect.buckaroo.Unit;
import com.loopperfect.buckaroo.io.IO;
import com.loopperfect.buckaroo.routines.Routines;

public final class CookbooksCommand implements CLICommand {

    private CookbooksCommand() {

    }

    @Override
    public int hashCode() {
        return 0;
    }

    @Override
    public boolean equals(final Object obj) {
        return this == obj || (obj != null && obj instanceof CookbooksCommand);
    }

    @Override
    public IO<Unit> routine() {
        return Routines.listCookBooks;
    }

    public static CookbooksCommand of() {
        return new CookbooksCommand();
    }
}