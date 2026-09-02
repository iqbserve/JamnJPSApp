/* Authored by iqbserve.de */

import { Logger } from 'core/logging.mjs';
import { moveArrayElement } from 'core/tools.mjs';
import { LazyFunction } from 'core/tools.mjs';
import { addFeature } from 'app/wb-features.mjs';
import { CommandDef } from 'core/data-classes.mjs';

/**
 * <pre>
 * Extensions WebApp customization interface file.
 * Place customization efforts in doCustomization() function.
 * </pre>
 */
export function doCustomization(config) {
    // just an example - positioning a sidebar topic item
    const topics = config.getTopicList();

    let topic = null;
    let item = null;
    if (topic = topics.find(topic => topic.text == "Server Commands")) {
        if (item = topic.items.find(item => item.feature == "cmdSampleBuildProject")) {
            moveArrayElement(item, topic.items.length, topic.items);
        }
    }
}

// -----------------------------------------------------------------
// Feature definitions placeholder 
// DO NOT change, edit, format or remove this custom placeholder
${ featureDefs }

Logger.info("Web App extension features installed");