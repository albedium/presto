package com.facebook.presto.execution;

import com.facebook.presto.event.query.QueryMonitor;
import com.facebook.presto.metadata.DataSourceType;
import com.facebook.presto.metadata.MetadataManager;
import com.facebook.presto.metadata.QualifiedTableName;
import com.facebook.presto.metadata.TableMetadata;
import com.facebook.presto.sql.analyzer.Session;
import com.facebook.presto.sql.tree.DropTable;
import com.facebook.presto.sql.tree.Statement;
import com.google.common.base.Preconditions;
import io.airlift.log.Logger;

import javax.inject.Inject;

import java.net.URI;

import static com.facebook.presto.metadata.MetadataUtil.createQualifiedTableName;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class DropTableExecution
        implements QueryExecution
{
    private static final Logger log = Logger.get(DropTableExecution.class);

    private final DropTable statement;
    private final MetadataManager metadataManager;
    private final Sitevars sitevars;

    private final QueryStateMachine stateMachine;

    DropTableExecution(QueryId queryId,
            String query,
            Session session,
            URI self,
            DropTable statement,
            MetadataManager metadataManager,
            QueryMonitor queryMonitor,
            Sitevars sitevars)
    {
        this.statement = statement;
        this.sitevars = sitevars;
        this.metadataManager = metadataManager;

        this.stateMachine = new QueryStateMachine(queryId, query, session, self, queryMonitor);
    }

    public void start()
    {
        try {
            checkState(sitevars.isDropEnabled(), "table dropping is disabled");

            // transition to starting
            if (!stateMachine.starting()) {
                // query already started or finished
                return;
            }

            stateMachine.getStats().recordExecutionStart();

            dropTable();

            stateMachine.finished();
        }
        catch (Exception e) {
            fail(e);
        }
    }

    @Override
    public void cancel()
    {
        stateMachine.cancel();
    }

    @Override
    public void fail(Throwable cause)
    {
        stateMachine.fail(cause);
    }

    @Override
    public void updateState(boolean forceUpdate)
    {
    }

    @Override
    public void cancelStage(StageId stageId)
    {
    }

    @Override
    public QueryInfo getQueryInfo()
    {
        return stateMachine.getQueryInfo();
    }

    private void dropTable()
    {
        QualifiedTableName tableName = createQualifiedTableName(stateMachine.getSession(), statement.getTableName());

        log.debug("Dropping %s", tableName);

        TableMetadata tableMetadata = metadataManager.getTable(tableName);
        Preconditions.checkState(tableMetadata != null, "Table %s does not exist", tableName);
        Preconditions.checkState(DataSourceType.NATIVE == tableMetadata.getTableHandle().get().getDataSourceType(), "Can drop only native tables");
        metadataManager.dropTable(tableMetadata);

        stateMachine.finished();
    }

    public static class DropTableExecutionFactory
            implements QueryExecutionFactory<DropTableExecution>
    {
        private final LocationFactory locationFactory;
        private final MetadataManager metadataManager;
        private final QueryMonitor queryMonitor;
        private final Sitevars sitevars;

        @Inject
        DropTableExecutionFactory(LocationFactory locationFactory,
                MetadataManager metadataManager,
                QueryMonitor queryMonitor,
                Sitevars sitevars)
        {
            this.locationFactory = checkNotNull(locationFactory, "locationFactory is null");
            this.metadataManager = checkNotNull(metadataManager, "metadataManager is null");
            this.queryMonitor = checkNotNull(queryMonitor, "queryMonitor is null");
            this.sitevars = checkNotNull(sitevars, "sitevars is null");
        }

        public DropTableExecution createQueryExecution(QueryId queryId, String query, Session session, Statement statement)
        {
            return new DropTableExecution(queryId,
                    query,
                    session,
                    locationFactory.createQueryLocation(queryId),
                    (DropTable) statement,
                    metadataManager,
                    queryMonitor,
                    sitevars);
        }
    }
}