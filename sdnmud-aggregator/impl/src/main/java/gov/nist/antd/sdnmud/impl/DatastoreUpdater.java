package gov.nist.antd.sdnmud.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutionException;

import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.sal.core.api.model.SchemaService;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNodeContainer;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.NormalizedNodeContainerBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableContainerNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.model.parser.api.YangSyntaxErrorException;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceException;
import org.opendaylight.yangtools.yang.model.util.SchemaContextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.stream.JsonReader;

public class DatastoreUpdater {
	
	private static final Logger LOG = LoggerFactory.getLogger(DatastoreUpdater.class);

	private SchemaService schemaService;

	private DOMDataBroker domDataBroker;
	
	
	private void importFromNormalizedNode(final DOMDataReadWriteTransaction rwTrx, final LogicalDatastoreType type,
			final NormalizedNode<?, ?> data) throws TransactionCommitFailedException, ReadFailedException, InterruptedException, ExecutionException {
		if (data instanceof NormalizedNodeContainer) {
			@SuppressWarnings("unchecked")
			YangInstanceIdentifier yid = YangInstanceIdentifier.create(data.getIdentifier());
			// rwTrx.put(type, yid, data);
			rwTrx.merge(type, yid, data);
			rwTrx.submit().get();
		} else {
			throw new IllegalStateException("Root node is not instance of NormalizedNodeContainer");
		}
	}

	public  void writeToDatastore(String jsonData, QName qname) throws TransactionCommitFailedException, IOException,
			ReadFailedException, SchemaSourceException, YangSyntaxErrorException, InterruptedException, ExecutionException {
		LOG.info("jsonData = " + jsonData);

		byte bytes[] = jsonData.getBytes();
		InputStream is = new ByteArrayInputStream(bytes);

		final NormalizedNodeContainerBuilder<?, ?, ?, ?> builder = ImmutableContainerNodeBuilder.create()
				.withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(qname));

		try (NormalizedNodeStreamWriter writer = ImmutableNormalizedNodeStreamWriter.from(builder)) {

			SchemaPath schemaPath = SchemaPath.create(true, qname);

			LOG.debug("SchemaPath " + schemaPath);

			SchemaNode parentNode = SchemaContextUtil.findNodeInSchemaContext(schemaService.getGlobalContext(),
					schemaPath.getPathFromRoot());

			LOG.debug("parentNode " + parentNode);

			// Create a jsonParser from the writer.
			try (JsonParserStream jsonParser = JsonParserStream.create(writer, schemaService.getGlobalContext(),
					parentNode)) {
				try (JsonReader reader = new JsonReader(new InputStreamReader(is))) {
					reader.setLenient(true);
					// The side effect of this parse is a write to the builder.
					jsonParser.parse(reader);
					DOMDataReadWriteTransaction rwTrx = domDataBroker.newReadWriteTransaction();
					importFromNormalizedNode(rwTrx, LogicalDatastoreType.CONFIGURATION, builder.build());
				}
			}
		}

	}
	
	
	public DatastoreUpdater(SdnmudProvider sdnmudProvider) {
		this.domDataBroker = sdnmudProvider.getDomDataBroker();
		this.schemaService = sdnmudProvider.getSchemaService();
	}
}
