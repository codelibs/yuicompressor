# YUICompressor ES6 Migration Plan

## 概要

このドキュメントは、YUICompressorをES6（ECMAScript 2015）およびそれ以降のJavaScript構文に対応させるための計画を記述する。

## 現状分析

### 使用技術
- **JavaScript Parser**: Mozilla Rhino 1.8.0
- **言語バージョン設定**: `Context.VERSION_1_8` (ES5.1相当)

### 現在サポートされている構文

#### 完全サポート（明示的なコード生成）
| 構文 | MungedCodeGeneratorでの処理 |
|------|---------------------------|
| `var` 宣言 | ✅ `visitVariableDeclaration` |
| `let` 宣言 | ✅ `visitVariableDeclaration` |
| `const` 宣言 | ✅ `visitVariableDeclaration` |
| 関数宣言/式 | ✅ `visitFunction` |
| 変数参照 | ✅ `visitName` |
| return文 | ✅ `visitReturnStatement` |
| ブロック `{}` | ✅ `visitBlock` |
| 数値リテラル | ✅ Number直接出力 |
| 文字列リテラル | ✅ `visitStringLiteral` |
| 代入 `=` | ✅ `visitInfixExpression` |
| 算術演算子 `+ - * /` | ✅ `visitInfixExpression` |
| 関数呼び出し | ✅ `visitFunctionCall` |
| プロパティアクセス `.` | ✅ `visitPropertyGet` |
| オブジェクトリテラル | ✅ `visitObjectLiteral` |
| 配列リテラル | ✅ `visitArrayLiteral` |

#### パース可能だが最適化されない構文（`toSource()`フォールバック）
- アロー関数 `() => {}`
- テンプレートリテラル `` `hello ${name}` ``
- クラス宣言/式
- デストラクチャリング `const {a, b} = obj`
- スプレッド演算子 `...arr`
- 比較演算子 `== != === !== < > <= >=`
- 論理演算子 `&& || !`
- ビット演算子 `& | ^ ~ << >> >>>`
- 三項演算子 `? :`
- インクリメント/デクリメント `++ --`
- if/else文
- for/while/do-while ループ
- for-of/for-in ループ
- switch文
- try/catch/finally
- throw文
- new演算子
- this キーワード
- async/await
- ジェネレーター関数

### 現在の問題点

1. **言語バージョンが古い**: `VERSION_1_8`（ES5.1）に設定されている
2. **多くのES6構文が`toSource()`フォールバック**: 変数のmungingが適用されない
3. **ブロックスコープ未対応**: `let`/`const`のブロックスコープが正しく処理されない
4. **ES6予約語が不足**: `let`, `const`, `await`, `yield`, `of` など

## 対応計画

### Phase 1: 基盤整備 [優先度: 高]

#### 1.1 パーサー設定の更新
- [ ] `Context.VERSION_1_8` → `Context.VERSION_ES6` に変更
- [ ] CompilerEnvironsの設定見直し

#### 1.2 ES6予約語の追加
```java
// JavaScriptCompressor.java の reserved セットに追加
reserved.add("let");
reserved.add("const");
reserved.add("await");
reserved.add("yield");
reserved.add("of");
reserved.add("async");
reserved.add("from");
reserved.add("get");
reserved.add("set");
```

#### 1.3 2文字/3文字の予約語リストからの除外
```java
twos.remove("of");
threes.remove("let");
threes.remove("get");
threes.remove("set");
```

---

### Phase 2: MungedCodeGeneratorの拡張（基本ES6構文） [優先度: 高]

#### 2.1 アロー関数のサポート
```java
case Token.ARROW:
    visitArrowFunction((ArrowFunctionNode) node);
    break;

private void visitArrowFunction(ArrowFunctionNode arrow) {
    // Parameters
    List<AstNode> params = arrow.getParams();
    if (params.size() == 1 && params.get(0) instanceof Name) {
        // Single parameter without parentheses
        visitNode(params.get(0));
    } else {
        output.append("(");
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) output.append(",");
            visitNode(params.get(i));
        }
        output.append(")");
    }

    output.append("=>");

    // Body
    AstNode body = arrow.getBody();
    visitNode(body);
}
```

#### 2.2 テンプレートリテラルのサポート
```java
case Token.TEMPLATE_LITERAL:
    visitTemplateLiteral((TemplateLiteral) node);
    break;

private void visitTemplateLiteral(TemplateLiteral template) {
    output.append('`');
    List<AstNode> elements = template.getElements();
    for (AstNode element : elements) {
        if (element instanceof TemplateCharacters) {
            output.append(((TemplateCharacters) element).getValue());
        } else {
            output.append("${");
            visitNode(element);
            output.append("}");
        }
    }
    output.append('`');
}
```

#### 2.3 その他の演算子の追加
```java
// 比較演算子
case Token.EQ: visitInfixExpression(node, "=="); break;
case Token.NE: visitInfixExpression(node, "!="); break;
case Token.SHEQ: visitInfixExpression(node, "==="); break;
case Token.SHNE: visitInfixExpression(node, "!=="); break;
case Token.LT: visitInfixExpression(node, "<"); break;
case Token.LE: visitInfixExpression(node, "<="); break;
case Token.GT: visitInfixExpression(node, ">"); break;
case Token.GE: visitInfixExpression(node, ">="); break;

// 論理演算子
case Token.AND: visitInfixExpression(node, "&&"); break;
case Token.OR: visitInfixExpression(node, "||"); break;
case Token.NOT: visitUnaryExpression(node, "!"); break;

// ビット演算子
case Token.BITAND: visitInfixExpression(node, "&"); break;
case Token.BITOR: visitInfixExpression(node, "|"); break;
case Token.BITXOR: visitInfixExpression(node, "^"); break;
case Token.BITNOT: visitUnaryExpression(node, "~"); break;
case Token.LSH: visitInfixExpression(node, "<<"); break;
case Token.RSH: visitInfixExpression(node, ">>"); break;
case Token.URSH: visitInfixExpression(node, ">>>"); break;

// その他の演算子
case Token.MOD: visitInfixExpression(node, "%"); break;
case Token.COMMA: visitInfixExpression(node, ","); break;
```

---

### Phase 3: MungedCodeGeneratorの拡張（制御構文） [優先度: 中]

#### 3.1 制御構文の追加
```java
// if文
case Token.IF:
    visitIfStatement((IfStatement) node);
    break;

private void visitIfStatement(IfStatement ifStmt) {
    output.append("if(");
    visitNode(ifStmt.getCondition());
    output.append(")");
    visitNode(ifStmt.getThenPart());

    AstNode elsePart = ifStmt.getElsePart();
    if (elsePart != null) {
        output.append("else ");
        visitNode(elsePart);
    }
}

// forループ
case Token.FOR:
    visitForLoop((ForLoop) node);
    break;

// whileループ
case Token.WHILE:
    visitWhileLoop((WhileLoop) node);
    break;

// do-while ループ
case Token.DO:
    visitDoLoop((DoLoop) node);
    break;

// switch文
case Token.SWITCH:
    visitSwitchStatement((SwitchStatement) node);
    break;
```

#### 3.2 その他の構文
```java
// 三項演算子
case Token.HOOK:
    visitConditionalExpression((ConditionalExpression) node);
    break;

// インクリメント/デクリメント
case Token.INC:
case Token.DEC:
    visitUpdateExpression((UpdateExpression) node);
    break;

// throw文
case Token.THROW:
    visitThrowStatement((ThrowStatement) node);
    break;

// try-catch-finally
case Token.TRY:
    visitTryStatement((TryStatement) node);
    break;

// new演算子
case Token.NEW:
    visitNewExpression((NewExpression) node);
    break;

// this
case Token.THIS:
    output.append("this");
    break;
```

---

### Phase 4: ScopeBuilderのブロックスコープ対応 [優先度: 中]

#### 4.1 ブロックスコープの追跡
```java
// Block内のlet/constを適切なスコープで管理
if (node instanceof Block) {
    Block block = (Block) node;
    // ブロック内のlet/constはブロックスコープに
    ScriptOrFnScope blockScope = new ScriptOrFnScope(braceNesting + 1, currentScope);
    blockScope.setBlockScope(true);
    scopeMap.put(block, blockScope);
    // ...
}
```

#### 4.2 アロー関数のスコープ
```java
// アロー関数は独自のスコープを持つ（thisは継承）
if (node instanceof ArrowFunctionNode) {
    ArrowFunctionNode arrow = (ArrowFunctionNode) node;
    ScriptOrFnScope arrowScope = new ScriptOrFnScope(braceNesting + 1, currentScope);
    arrowScope.setArrowFunction(true);
    scopeMap.put(arrow, arrowScope);
    // パラメータを登録
    for (AstNode param : arrow.getParams()) {
        if (param instanceof Name) {
            arrowScope.declareIdentifier(((Name) param).getIdentifier());
        }
    }
}
```

---

### Phase 5: 高度なES6機能 [優先度: 中]

#### 5.1 クラス宣言/式
```java
case Token.CLASS:
    visitClassNode(node);
    break;

private void visitClassNode(AstNode node) {
    // ClassNode handling
}
```

#### 5.2 デストラクチャリング
```java
// 配列デストラクチャリング
case Token.ARRAY_COMP:  // or specific destructuring token
    visitArrayDestructuring(node);
    break;

// オブジェクトデストラクチャリング
// ObjectLiteralとしてパースされるため、コンテキストで判断
```

#### 5.3 スプレッド/レスト演算子
```java
case Token.SPREAD:
    visitSpreadExpression(node);
    break;
```

---

### Phase 6: モジュール構文（将来対応） [優先度: 低]

- import/export 構文
- dynamic import `import()`
- export default

---

## 実装優先順位

### 高優先度（まず対応）
1. パーサー設定の更新（VERSION_ES6）
2. ES6予約語の追加
3. アロー関数のコード生成
4. 比較/論理/ビット演算子の追加
5. 制御構文（if/for/while）の追加

### 中優先度（次に対応）
6. テンプレートリテラルのコード生成
7. ブロックスコープの正確な処理
8. クラス宣言のコード生成
9. デストラクチャリングの処理

### 低優先度（将来対応）
10. async/await
11. ジェネレーター
12. モジュール構文

---

## テスト計画

### 新規テストケース
```java
@Test
public void testArrowFunction() {
    // 基本アロー関数
    assertCompression("const f=x=>x*2;", "const f=x=>x*2;");

    // 複数パラメータ
    assertCompression("const f=(x,y)=>x+y;", "const f=(a,b)=>a+b;");

    // ブロックボディ
    assertCompression("const f=x=>{return x*2;};", "const f=a=>{return a*2;};");
}

@Test
public void testTemplateLiteral() {
    assertCompression("`hello ${name}`;", "`hello ${a}`;");
}

@Test
public void testClass() {
    assertCompression(
        "class Foo{constructor(x){this.x=x;}}",
        "class Foo{constructor(a){this.x=a;}}"
    );
}
```

---

## リスクと軽減策

### リスク1: Rhino 1.8.0のES6サポートの限界
- **軽減策**: `toSource()`フォールバックを維持し、正確性を優先

### リスク2: 後方互換性の破壊
- **軽減策**: 既存テストを全て通すことを確認

### リスク3: パフォーマンス低下
- **軽減策**: 重要なノードタイプの明示的処理を優先

---

## 完了基準

1. [x] ES6構文をパースしてもエラーにならない
2. [x] アロー関数の変数がmungingされる
3. [x] `let`/`const`のブロックスコープが正しく処理される
4. [ ] 既存のテストが全て通る（ネットワーク接続時に確認）
5. [x] 新規ES6テストが追加され、通る

---

## 実装完了項目（Phase 1-5）

### Phase 1: 基盤整備 ✅
- [x] パーサー設定を`Context.VERSION_ES6`に更新
- [x] ES6予約語を追加（`let`, `const`, `await`, `yield`, `of`, `async`, `from`, `get`, `set`, `target`, `meta`）
- [x] 2文字/3文字の変数名リストからES6キーワードを除外

### Phase 2: MungedCodeGenerator拡張（基本ES6構文） ✅
- [x] アロー関数のサポート（`=>`構文を保持）
- [x] テンプレートリテラルのサポート（バッククォートと`${}`補間）
- [x] 比較演算子（`==`, `!=`, `===`, `!==`, `<`, `<=`, `>`, `>=`）
- [x] 論理演算子（`&&`, `||`, `!`）
- [x] ビット演算子（`&`, `|`, `^`, `~`, `<<`, `>>`, `>>>`）
- [x] その他の演算子（`%`, `**`, `,`, `in`, `instanceof`）

### Phase 3: MungedCodeGenerator拡張（制御構文） ✅
- [x] if/else文
- [x] for/while/do-whileループ
- [x] for-in/for-ofループ
- [x] switch/case/default文
- [x] try/catch/finally文
- [x] break/continue文
- [x] throw文
- [x] labeled文
- [x] with文

### Phase 4: ScopeBuilder拡張 ✅
- [x] デストラクチャリングパターンからの変数抽出
- [x] for-of/for-inループの変数スコープ
- [x] catchブロックの変数スコープ
- [x] オブジェクトプロパティキーの除外

### Phase 5: テストケース追加 ✅
- [x] ES6SupportTest.javaを新規作成
- [x] 40+のES6機能テストケースを追加
