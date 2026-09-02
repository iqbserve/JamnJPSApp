
addFeature('toolsDBConnections', new LazyFunction('features/db-connections.mjs', 'getView'),
    { topic: 'Tools', item: 'DB Connections' });