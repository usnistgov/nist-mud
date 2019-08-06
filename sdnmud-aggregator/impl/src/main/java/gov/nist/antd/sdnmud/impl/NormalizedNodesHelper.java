package gov.nist.antd.sdnmud.impl;
/*
 * Copyright (c) 2017 Brocade Communications Systems, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

import org.opendaylight.controller.md.sal.binding.impl.BindingToNormalizedNodeCodec;
import org.opendaylight.yangtools.binding.data.codec.gen.impl.DataObjectSerializerGenerator;
import org.opendaylight.yangtools.binding.data.codec.gen.impl.StreamWriterGenerator;
import org.opendaylight.yangtools.binding.data.codec.impl.BindingNormalizedNodeCodecRegistry;
import org.opendaylight.mdsal.binding.generator.impl.GeneratedClassLoadingStrategy;
import org.opendaylight.mdsal.binding.generator.util.BindingRuntimeContext;
import org.opendaylight.mdsal.binding.generator.util.JavassistUtils;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.util.SchemaContextUtil;


import javassist.ClassPool;

/**
 * Helper class to provide easy way to encode/decode of DOM objects.
 * 
 * @author <a href="mailto:rkosegi@brocade.com">Richard Kosegi</a>
 *
 */
public class NormalizedNodesHelper {
    private static NormalizedNodesHelper instance = null;
    private BindingToNormalizedNodeCodec bnnc;

    private NormalizedNodesHelper(SchemaContext schemaContext) {
        DataObjectSerializerGenerator generator = StreamWriterGenerator
                .create(JavassistUtils.forClassPool(ClassPool.getDefault()));
        BindingNormalizedNodeCodecRegistry codecRegistry = new BindingNormalizedNodeCodecRegistry(generator);
        BindingRuntimeContext context = BindingRuntimeContext
                .create(GeneratedClassLoadingStrategy.getTCCLClassLoadingStrategy(), schemaContext);
        codecRegistry.onBindingRuntimeContextUpdated(context);
        bnnc = new BindingToNormalizedNodeCodec(GeneratedClassLoadingStrategy.getTCCLClassLoadingStrategy(),
                codecRegistry);
        bnnc.onGlobalContextUpdated(schemaContext);
    }

    public static void init(SdnmudProvider provider) {
    	SchemaContext schemaContext = provider.getDomSchemaService().getGlobalContext();
        if (null == instance) {
            instance = new NormalizedNodesHelper(schemaContext);
        }
    }

    public static BindingToNormalizedNodeCodec getBindingToNormalizedNodeCodec() {
        return instance.bnnc;
    }
    
    
}
