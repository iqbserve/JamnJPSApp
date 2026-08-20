/* Authored by iqbserve.de */

/**
 * Html layout source for a standard work view.
 */
export const WorkViewHtml = (): string => {
	return `
<div class="work-view" style="visibility: hidden;">
	<div id="work-view-header" class="work-view-header-container">
		<div class="work-view-header">
			<!-- the standard header icons left -->
			<span id="wkv-header-iconbar-left" class="header-iconbar header-left"></span>
			<!-- the view popup menu -->
			<div id="header-menu" class="wkv-header-menu"></div>
			<!-- the title -->
			<span id="view-title" class="wkv-header-title">Unknown</span>
			<!-- the standard header icons right -->
			<span id="wkv-header-iconbar-right" class="header-iconbar header-right"></span>
		</div>
		<div id="wkv-header-progressbar" class="wkv-header-progressbar">
			<div class="header-progress-value"></div>
		</div>
	</div>
	<div id="work-view-body" class="work-view-body">
		<!-- the main working area -->
		<div id="work-view-workarea" class="work-view-workarea flex-one"></div>
		<div id="work-view-sidepanel-splitter" class="splitter vsplit wkv-sidepanel-splitter"></div>
		<div id="work-view-sidepanel" class="work-view-sidepanel"></div>
	</div>
</div>
`};