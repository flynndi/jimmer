package org.babyfish.jimmer.sql.event.binlog.impl;

import com.fasterxml.jackson.databind.module.SimpleModule;

class BinLogModuleV2 extends SimpleModule {

    private final BinLogParser parser;

    BinLogModuleV2(BinLogParser parser) {
        this.parser = parser;
    }

    @Override
    public void setupModule(SetupContext ctx) {
        super.setupModule(ctx);
        ctx.addDeserializers(new BinLogDeserializersV2(parser));
    }
}
