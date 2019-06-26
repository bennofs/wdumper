(function () {
  'use strict';

  function parseQuery (query) {
    var isId = false;
    var isClass = false;
    var tag = '';
    var id = '';
    var className = '';
    for (var i = 0; i < query.length; i++) {
      var char = query[i];
      if (char === '.') {
        isClass = true;
        isId = false;
        if (className.length > 0) {
          className += ' ';
        }
      } else if (char === '#') {
        isId = true;
        isClass = false;
      } else if (isId) {
        id += char;
      } else if (isClass) {
        className += char;
      } else {
        tag += char;
      }
    }

    return {
      tag: tag || 'div',
      id: id,
      className: className
    };
  }

  function createElement (query, ns) {
    var ref = parseQuery(query);
    var tag = ref.tag;
    var id = ref.id;
    var className = ref.className;
    var element = ns ? document.createElementNS(ns, tag) : document.createElement(tag);

    if (id) {
      element.id = id;
    }

    if (className) {
      if (ns) {
        element.setAttribute('class', className);
      } else {
        element.className = className;
      }
    }

    return element;
  }

  function unmount (parent, child) {
    var parentEl = getEl(parent);
    var childEl = getEl(child);

    if (child === childEl && childEl.__redom_view) {
      // try to look up the view if not provided
      child = childEl.__redom_view;
    }

    if (childEl.parentNode) {
      doUnmount(child, childEl, parentEl);

      parentEl.removeChild(childEl);
    }

    return child;
  }

  function doUnmount (child, childEl, parentEl) {
    var hooks = childEl.__redom_lifecycle;

    if (hooksAreEmpty(hooks)) {
      childEl.__redom_mounted = false;
      return;
    }

    var traverse = parentEl;

    if (childEl.__redom_mounted) {
      trigger(childEl, 'onunmount');
    }

    while (traverse) {
      var parentHooks = traverse.__redom_lifecycle || {};

      for (var hook in hooks) {
        if (parentHooks[hook]) {
          parentHooks[hook] -= hooks[hook];
        }
      }

      if (hooksAreEmpty(parentHooks)) {
        traverse.__redom_lifecycle = null;
      }

      traverse = traverse.parentNode;
    }
  }

  function hooksAreEmpty (hooks) {
    if (hooks == null) {
      return true;
    }
    for (var key in hooks) {
      if (hooks[key]) {
        return false;
      }
    }
    return true;
  }

  var hookNames = ['onmount', 'onremount', 'onunmount'];
  var shadowRootAvailable = typeof window !== 'undefined' && 'ShadowRoot' in window;

  function mount (parent, child, before, replace) {
    var parentEl = getEl(parent);
    var childEl = getEl(child);

    if (child === childEl && childEl.__redom_view) {
      // try to look up the view if not provided
      child = childEl.__redom_view;
    }

    if (child !== childEl) {
      childEl.__redom_view = child;
    }

    var wasMounted = childEl.__redom_mounted;
    var oldParent = childEl.parentNode;

    if (wasMounted && (oldParent !== parentEl)) {
      doUnmount(child, childEl, oldParent);
    }

    if (before != null) {
      if (replace) {
        parentEl.replaceChild(childEl, getEl(before));
      } else {
        parentEl.insertBefore(childEl, getEl(before));
      }
    } else {
      parentEl.appendChild(childEl);
    }

    doMount(child, childEl, parentEl, oldParent);

    return child;
  }

  function trigger (el, eventName) {
    if (eventName === 'onmount' || eventName === 'onremount') {
      el.__redom_mounted = true;
    } else if (eventName === 'onunmount') {
      el.__redom_mounted = false;
    }

    var hooks = el.__redom_lifecycle;

    if (!hooks) {
      return;
    }

    var view = el.__redom_view;
    var hookCount = 0;

    view && view[eventName] && view[eventName]();

    for (var hook in hooks) {
      if (hook) {
        hookCount++;
      }
    }

    if (hookCount) {
      var traverse = el.firstChild;

      while (traverse) {
        var next = traverse.nextSibling;

        trigger(traverse, eventName);

        traverse = next;
      }
    }
  }

  function doMount (child, childEl, parentEl, oldParent) {
    var hooks = childEl.__redom_lifecycle || (childEl.__redom_lifecycle = {});
    var remount = (parentEl === oldParent);
    var hooksFound = false;

    for (var i = 0, list = hookNames; i < list.length; i += 1) {
      var hookName = list[i];

      if (!remount) { // if already mounted, skip this phase
        if (child !== childEl) { // only Views can have lifecycle events
          if (hookName in child) {
            hooks[hookName] = (hooks[hookName] || 0) + 1;
          }
        }
      }
      if (hooks[hookName]) {
        hooksFound = true;
      }
    }

    if (!hooksFound) {
      childEl.__redom_mounted = true;
      return;
    }

    var traverse = parentEl;
    var triggered = false;

    if (remount || (traverse && traverse.__redom_mounted)) {
      trigger(childEl, remount ? 'onremount' : 'onmount');
      triggered = true;
    }

    while (traverse) {
      var parent = traverse.parentNode;
      var parentHooks = traverse.__redom_lifecycle || (traverse.__redom_lifecycle = {});

      for (var hook in hooks) {
        parentHooks[hook] = (parentHooks[hook] || 0) + hooks[hook];
      }

      if (triggered) {
        break;
      } else {
        if (traverse === document ||
          (shadowRootAvailable && (traverse instanceof window.ShadowRoot)) ||
          (parent && parent.__redom_mounted)
        ) {
          trigger(traverse, remount ? 'onremount' : 'onmount');
          triggered = true;
        }
        traverse = parent;
      }
    }
  }

  function setStyle (view, arg1, arg2) {
    var el = getEl(view);

    if (typeof arg1 === 'object') {
      for (var key in arg1) {
        setStyleValue(el, key, arg1[key]);
      }
    } else {
      setStyleValue(el, arg1, arg2);
    }
  }

  function setStyleValue (el, key, value) {
    if (value == null) {
      el.style[key] = '';
    } else {
      el.style[key] = value;
    }
  }

  /* global SVGElement */

  var xlinkns = 'http://www.w3.org/1999/xlink';

  function setAttrInternal (view, arg1, arg2, initial) {
    var el = getEl(view);

    var isObj = typeof arg1 === 'object';

    if (isObj) {
      for (var key in arg1) {
        setAttrInternal(el, key, arg1[key], initial);
      }
    } else {
      var isSVG = el instanceof SVGElement;
      var isFunc = typeof arg2 === 'function';

      if (arg1 === 'style' && typeof arg2 === 'object') {
        setStyle(el, arg2);
      } else if (isSVG && isFunc) {
        el[arg1] = arg2;
      } else if (arg1 === 'dataset') {
        setData(el, arg2);
      } else if (!isSVG && (arg1 in el || isFunc) && (arg1 !== 'list')) {
        el[arg1] = arg2;
      } else {
        if (isSVG && (arg1 === 'xlink')) {
          setXlink(el, arg2);
          return;
        }
        if (initial && arg1 === 'class') {
          arg2 = el.className + ' ' + arg2;
        }
        if (arg2 == null) {
          el.removeAttribute(arg1);
        } else {
          el.setAttribute(arg1, arg2);
        }
      }
    }
  }

  function setXlink (el, arg1, arg2) {
    if (typeof arg1 === 'object') {
      for (var key in arg1) {
        setXlink(el, key, arg1[key]);
      }
    } else {
      if (arg2 != null) {
        el.setAttributeNS(xlinkns, arg1, arg2);
      } else {
        el.removeAttributeNS(xlinkns, arg1, arg2);
      }
    }
  }

  function setData (el, arg1, arg2) {
    if (typeof arg1 === 'object') {
      for (var key in arg1) {
        setData(el, key, arg1[key]);
      }
    } else {
      if (arg2 != null) {
        el.dataset[arg1] = arg2;
      } else {
        delete el.dataset[arg1];
      }
    }
  }

  function text (str) {
    return document.createTextNode((str != null) ? str : '');
  }

  function parseArgumentsInternal (element, args, initial) {
    for (var i = 0, list = args; i < list.length; i += 1) {
      var arg = list[i];

      if (arg !== 0 && !arg) {
        continue;
      }

      var type = typeof arg;

      if (type === 'function') {
        arg(element);
      } else if (type === 'string' || type === 'number') {
        element.appendChild(text(arg));
      } else if (isNode(getEl(arg))) {
        mount(element, arg);
      } else if (arg.length) {
        parseArgumentsInternal(element, arg, initial);
      } else if (type === 'object') {
        setAttrInternal(element, arg, null, initial);
      }
    }
  }

  function ensureEl (parent) {
    return typeof parent === 'string' ? html(parent) : getEl(parent);
  }

  function getEl (parent) {
    return (parent.nodeType && parent) || (!parent.el && parent) || getEl(parent.el);
  }

  function isNode (arg) {
    return arg && arg.nodeType;
  }

  var htmlCache = {};

  function html (query) {
    var args = [], len = arguments.length - 1;
    while ( len-- > 0 ) args[ len ] = arguments[ len + 1 ];

    var element;

    var type = typeof query;

    if (type === 'string') {
      element = memoizeHTML(query).cloneNode(false);
    } else if (isNode(query)) {
      element = query.cloneNode(false);
    } else if (type === 'function') {
      var Query = query;
      element = new (Function.prototype.bind.apply( Query, [ null ].concat( args) ));
    } else {
      throw new Error('At least one argument required');
    }

    parseArgumentsInternal(getEl(element), args, true);

    return element;
  }

  var el = html;

  html.extend = function extendHtml (query) {
    var args = [], len = arguments.length - 1;
    while ( len-- > 0 ) args[ len ] = arguments[ len + 1 ];

    var clone = memoizeHTML(query);

    return html.bind.apply(html, [ this, clone ].concat( args ));
  };

  function memoizeHTML (query) {
    return htmlCache[query] || (htmlCache[query] = createElement(query));
  }

  function setChildren (parent) {
    var children = [], len = arguments.length - 1;
    while ( len-- > 0 ) children[ len ] = arguments[ len + 1 ];

    var parentEl = getEl(parent);
    var current = traverse(parent, children, parentEl.firstChild);

    while (current) {
      var next = current.nextSibling;

      unmount(parent, current);

      current = next;
    }
  }

  function traverse (parent, children, _current) {
    var current = _current;

    var childEls = new Array(children.length);

    for (var i = 0; i < children.length; i++) {
      childEls[i] = children[i] && getEl(children[i]);
    }

    for (var i$1 = 0; i$1 < children.length; i$1++) {
      var child = children[i$1];

      if (!child) {
        continue;
      }

      var childEl = childEls[i$1];

      if (childEl === current) {
        current = current.nextSibling;
        continue;
      }

      if (isNode(childEl)) {
        var next = current && current.nextSibling;
        var exists = child.__redom_index != null;
        var replace = exists && next === childEls[i$1 + 1];

        mount(parent, child, current, replace);

        if (replace) {
          current = next;
        }

        continue;
      }

      if (child.length != null) {
        current = traverse(parent, child, current);
      }
    }

    return current;
  }

  var ListPool = function ListPool (View, key, initData) {
    this.View = View;
    this.initData = initData;
    this.oldLookup = {};
    this.lookup = {};
    this.oldViews = [];
    this.views = [];

    if (key != null) {
      this.key = typeof key === 'function' ? key : propKey(key);
    }
  };
  ListPool.prototype.update = function update (data, context) {
    var ref = this;
      var View = ref.View;
      var key = ref.key;
      var initData = ref.initData;
    var keySet = key != null;

    var oldLookup = this.lookup;
    var newLookup = {};

    var newViews = new Array(data.length);
    var oldViews = this.views;

    for (var i = 0; i < data.length; i++) {
      var item = data[i];
      var view = (void 0);

      if (keySet) {
        var id = key(item);

        view = oldLookup[id] || new View(initData, item, i, data);
        newLookup[id] = view;
        view.__redom_id = id;
      } else {
        view = oldViews[i] || new View(initData, item, i, data);
      }
      view.update && view.update(item, i, data, context);

      var el = getEl(view.el);

      el.__redom_view = view;
      newViews[i] = view;
    }

    this.oldViews = oldViews;
    this.views = newViews;

    this.oldLookup = oldLookup;
    this.lookup = newLookup;
  };

  function propKey (key) {
    return function (item) {
      return item[key];
    };
  }

  var List = function List (parent, View, key, initData) {
    this.__redom_list = true;
    this.View = View;
    this.initData = initData;
    this.views = [];
    this.pool = new ListPool(View, key, initData);
    this.el = ensureEl(parent);
    this.keySet = key != null;
  };
  List.prototype.update = function update (data, context) {
      if ( data === void 0 ) data = [];

    var ref = this;
      var keySet = ref.keySet;
    var oldViews = this.views;

    this.pool.update(data, context);

    var ref$1 = this.pool;
      var views = ref$1.views;
      var lookup = ref$1.lookup;

    if (keySet) {
      for (var i = 0; i < oldViews.length; i++) {
        var oldView = oldViews[i];
        var id = oldView.__redom_id;

        if (lookup[id] == null) {
          oldView.__redom_index = null;
          unmount(this, oldView);
        }
      }
    }

    for (var i$1 = 0; i$1 < views.length; i$1++) {
      var view = views[i$1];

      view.__redom_index = i$1;
    }

    setChildren(this, views);

    if (keySet) {
      this.lookup = lookup;
    }
    this.views = views;
  };

  List.extend = function extendList (parent, View, key, initData) {
    return List.bind(List, parent, View, key, initData);
  };

  var buildRadioGroup = (new /** @class */ (function () {
      function class_1() {
          var _this = this;
          this.sequence = 0;
          this.buildRadioGroup = function (initial, choices, handler) {
              var radioName = "radio-" + _this.sequence;
              _this.sequence++;
              var node = el("li.radio-group", Object.keys(choices).map(function (value) {
                  var label = choices[value];
                  return el("li.radio-group--option", [
                      el("input", { "type": "radio", "name": radioName, "value": value, "id": radioName + "-" + value, "checked": value == initial }),
                      el("label", { "for": radioName + "-" + value }, label)
                  ]);
              }));
              node.addEventListener("change", function (event) {
                  var target = event.target;
                  handler(target.value);
              });
              return node;
          };
      }
      return class_1;
  }())).buildRadioGroup;
  var SinglePropertyMatcher = /** @class */ (function () {
      function SinglePropertyMatcher(model) {
          var _this = this;
          this.model = model;
          var group = buildRadioGroup(this.model.type, {
              "anyvalue": "exists",
              "entityid": "entity",
          }, function (ty) { return _this.setType(ty); });
          this.propertyEl = el("input", { "type": "text", placeholder: "P31" });
          this.propertyEl.addEventListener("blur", function () {
              _this.model.property = _this.propertyEl.value;
          });
          this.valueEl = el("input", { "type": "text", placeholder: "Q5" });
          this.valueEl.addEventListener("blur", function () {
              _this.model.value = _this.valueEl.value;
          });
          this.el = el("li.form-line.prop-constraint", [
              this.propertyEl,
              group,
              this.valueEl
          ]);
          this.sync();
      }
      SinglePropertyMatcher.prototype.setType = function (type) {
          this.model.type = type;
          if (type == "entityid") {
              this.model.value = this.valueEl.value;
          }
          else {
              delete this.model.value;
          }
          this.sync();
      };
      SinglePropertyMatcher.prototype.sync = function () {
          this.valueEl.classList.toggle("hide", this.model.type != "entityid");
          this.propertyEl.value = this.model.property;
          if (this.model.type == "entityid") {
              this.valueEl.value = this.model.value;
          }
      };
      return SinglePropertyMatcher;
  }());
  var PropertyEntityMatcher = /** @class */ (function () {
      function PropertyEntityMatcher(model, id, remove) {
          var _this = this;
          this.model = model;
          var typeGroupEl = buildRadioGroup(this.model.type, {
              "item": "item",
              "property": "property",
              "lexeme": "lexeme"
          }, function (type) { _this.model.type = type; });
          this.propertyViews = [];
          var addButton = el("button", "+");
          addButton.addEventListener("click", function () { return _this.add(); });
          this.el = el(".form-group", [
              el(".form-group--label", "Property"),
              el(".form-group--main", el(".form-line", el("p.form-label", "entity type"), typeGroupEl), el(".form-line", el("p.form-label", "properties"), addButton), this.propertiesEl = el("ul"))
          ]);
      }
      PropertyEntityMatcher.prototype.add = function () {
          var initial = {
              property: "P31",
              type: "anyvalue",
              truthy: false
          };
          this.model.properties.push(initial);
          var view = new SinglePropertyMatcher(initial);
          this.propertyViews.push(view);
          this.propertiesEl.appendChild(view.el);
      };
      return PropertyEntityMatcher;
  }());
  var EntityFiltersView = /** @class */ (function () {
      function EntityFiltersView(container, filters) {
          var _this = this;
          this.container = container;
          this.nextId = 0;
          this.matchers = {};
          for (var _i = 0, filters_1 = filters; _i < filters_1.length; _i++) {
              var filter = filters_1[_i];
              this.add(filter);
          }
          document.getElementById("add-property-matcher").addEventListener("click", function () {
              _this.add({
                  "type": "item",
                  "properties": []
              });
          });
      }
      EntityFiltersView.prototype.remove = function (id) {
          // unmount
          this.matchers[id].el.remove();
          delete this.matchers[id];
      };
      EntityFiltersView.prototype.add = function (filter) {
          var _this = this;
          var id = this.nextId;
          var view = new PropertyEntityMatcher(filter, id, function () { return _this.remove(id); });
          this.matchers[this.nextId] = view;
          this.nextId += 1;
          // mount the new element
          this.container.appendChild(view.el);
      };
      return EntityFiltersView;
  }());
  var StatementFiltersView = /** @class */ (function () {
      function StatementFiltersView(container, model) {
          this.container = container;
          this.nextId = 0;
          this.matchers = {};
          // for (const filter of filters) {
          //     this.add(filter)
          // }
          // document.getElementById("add-property-matcher").addEventListener("click", () => {
          //     this.add({
          //         "type": "item",
          //         "properties": []
          //     })
          // })
      }
      return StatementFiltersView;
  }());
  var AdditionalSettingsView = /** @class */ (function () {
      function AdditionalSettingsView() {
      }
      return AdditionalSettingsView;
  }());
  var DumpSpecView = /** @class */ (function () {
      function DumpSpecView(parent, init) {
          this.parent = parent;
          this.model = init;
          var entityFiltersEl = document.getElementById("entity-filters");
          var statementFiltersEl = document.getElementById("statement-filters");
          var additionalSettingsEl = document.getElementById("additional-settings");
          this.entityFiltersView = new EntityFiltersView(document.getElementById("entity-filters"), this.model.entities);
          this.statementFiltersView = new StatementFiltersView(document.getElementById("statement-filters"), this.model.statements);
          this.additionalSettingsView = new AdditionalSettingsView();
      }
      return DumpSpecView;
  }());
  var initSpec = {
      entities: [{
              type: "item",
              properties: []
          }],
      statements: [],
      languages: [],
      labels: true,
      descriptions: true,
      aliases: true,
      truthy: false,
      meta: true,
      sitelinks: true
  };
  var mainEl = document.getElementById("main");
  window['view'] = new DumpSpecView(mainEl, initSpec);

}());
//# sourceMappingURL=main.js.map
