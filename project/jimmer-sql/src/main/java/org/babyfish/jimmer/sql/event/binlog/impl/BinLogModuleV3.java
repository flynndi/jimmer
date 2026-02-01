package org.babyfish.jimmer.sql.event.binlog.impl;

import tools.jackson.databind.module.SimpleModule;

class BinLogModuleV3 extends SimpleModule {

    private final BinLogParser parser;

    BinLogModuleV3(BinLogParser parser) {
        this.parser = parser;
    }

    @Override
    public void setupModule(SetupContext ctx) {
        super.setupModule(ctx);
        ctx.addDeserializers(new BinLogDeserializersV3(parser));
    }
}
