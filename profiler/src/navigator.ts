import { Comparable } from './util.js';

export class Point {
    constructor(readonly x: number, readonly y: number) { }

    pointwiseMax(other: Point): Point {
        return new Point(
            Math.max(this.x, other.x),
            Math.max(this.y, other.y)
        )
    }

    pointwiseMin(other: Point): Point {
        return new Point(
            Math.min(this.x, other.x),
            Math.min(this.y, other.y)
        )
    }

    minus(other: Point): Size | null {
        let x = this.x - other.x;
        let y = this.y - other.y;
        if (x < 0 || y < 0)
            return null;
        return new Size(x, y);
    }

    static zero(): Point {
        return new Point(0, 0);
    }
}

export class Size {
    constructor(readonly w: number, readonly h: number) {
        console.assert(w >= 0);
        console.assert(h >= 0);
    }

    static zero(): Size {
        return new Size(0, 0);
    }
}

export class Rectangle {
    constructor(readonly origin: Point, readonly size: Size) { }

    bottomRight(): Point {
        return new Point(
            this.origin.x + this.size.w,
            this.origin.y + this.size.h
        )
    }

    /** Create a rectangle from two corners; returns null if the bottomRight
     * corner is not to the bottom or right to the topLeft corner. */
    static fromPoints(topLeft: Point, bottomRight: Point): Rectangle | null {
        if (topLeft.x > bottomRight.x)
            return null;
        if (topLeft.y > bottomRight.y)
            return null;
        return new Rectangle(topLeft, bottomRight.minus(topLeft)!);
    }

    // Compute the intersection between two rectangles
    intersect(other: Rectangle): Rectangle | null {
        const topLeft = this.origin.pointwiseMax(other.origin);
        const bottomRight = this.bottomRight().pointwiseMin(other.bottomRight());
        return Rectangle.fromPoints(topLeft, bottomRight);
    }
}

// Displays zoom/pan information in a rectangular page
export class ViewNavigator {
    /*
        Displayed as:
       ------------ root = visualized object
       |          |
       |          |
       |   =====  | viewport = part visible on screen
       |   |   |  |
       |   =====  |
       ------------
    */

    readonly MAX_WIDTH = 100;
    readonly MAX_HEIGHT = 100;

    root: HTMLDivElement;
    viewport: HTMLDivElement;

    constructor(parent: HTMLElement) {
        this.root = document.createElement("div");
        this.root.id = "navigator";
        this.root.style.backgroundColor = 'rgb(220, 220, 220)';
        this.root.style.position = "absolute";
        this.root.style.left = "0";
        this.root.style.bottom = "0";

        this.viewport = document.createElement("div");
        this.viewport.id = "viewport";
        this.viewport.style.backgroundColor = `rgb(100, 100, 100)`;
        this.root.appendChild(this.viewport);

        parent.appendChild(this.root);
    }

    /** Set the view parameters
     * @param pageSize: size of the page containing the object rendering
     * @param upperLeft: coordinates in page of the upper left corner of the rendering
     * @parma renderedSize: size of the rendered object
     */
    setViewParameters(pageSize: Size, upperLeft: Point, renderedSize: Size) {
        // TODO
        return;

        const pageRect = new Rectangle(Point.zero(), pageSize);
        const viewRect = new Rectangle(upperLeft, renderedSize);
        let common = pageRect.intersect(viewRect);
        if (common == null)
            common = new Rectangle(Point.zero(), Size.zero());

        const tall = renderedSize.h > renderedSize.w;
        let h, w;
        if (tall) {
            h = this.MAX_HEIGHT;
            w = h * renderedSize.w / renderedSize.h;
        } else {
            w = this.MAX_WIDTH;
            h = w * renderedSize.h / renderedSize.w;
        }
        this.root.style.width = `${w}px`;
        this.root.style.height = `${h}px`;

        this.viewport.style.left = `${common.origin.x}px`;
        this.viewport.style.top = `${common.origin.y}px`;
        this.viewport.style.width = `${common.size.w}px`;
        this.viewport.style.height = `${common.size.h}px`;
    }
}