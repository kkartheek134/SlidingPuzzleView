package com.luxiliu.android.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.support.v4.view.GestureDetectorCompat;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class SlidingPuzzleView extends View implements GestureDetector.OnGestureListener {
    private static final int MIN_NUM_ROWS = 2;
    private static final int MIN_NUM_COLUMNS = 2;

    private GestureDetectorCompat mGestureDetectorCompat;

    // Attributes
    private Drawable mDrawable;
    private Bitmap mBitmap;
    private int mNumRows;
    private int mNumColumns;
    private int mEmptyTileIndex;

    // Runtime data
    private Rect mSrcRect = new Rect();
    private Rect mDstRect = new Rect();
    private Rect mTextRect = new Rect();
    private Paint mBorderPaint = new Paint();
    private Tile[][] mTiles;
    private DragEvent mDragEvent;
    private Position mEmptyPosition;

    public SlidingPuzzleView(Context context) {
        this(context, null);
    }

    public SlidingPuzzleView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SlidingPuzzleView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        init(context, attrs);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Scale and crop source mBitmap if necessary
        if (mBitmap == null || mBitmap.isRecycled()) {
            mBitmap = getScaledCroppedSourceBitmap();
        }

        // Background color
        canvas.drawColor(Color.BLACK);

        for (int row = 0; row < mNumColumns; row++) {
            for (int column = 0; column < mNumRows; column++) {
                Tile tile = mTiles[row][column];

                // Get tile's position and index
                int xOffset = getDrawingHorizontalOffset(row, column);
                int yOffset = getDrawingVerticalOffset(row, column);

                // Calculate tile's area on the screen
                mDstRect.left = column * getTileWidthInPixel() + xOffset;
                mDstRect.top = row * getTileHeightInPixel() + yOffset;
                mDstRect.right = mDstRect.left + getTileWidthInPixel();
                mDstRect.bottom = mDstRect.top + getTileHeightInPixel();

                if (tile.index != mEmptyTileIndex) {
                    // Draw tile's mBitmap from correct part of the whole mBitmap
                    mSrcRect.left = tile.index % mNumRows * getTileWidthInPixel();
                    mSrcRect.top = tile.index / mNumColumns * getTileHeightInPixel();
                    mSrcRect.right = mSrcRect.left + getTileWidthInPixel();
                    mSrcRect.bottom = mSrcRect.top + getTileHeightInPixel();

                    canvas.drawBitmap(mBitmap, mSrcRect, mDstRect, null);

                    // Draw tile's border
                    mBorderPaint.setColor(Color.WHITE);
                    mBorderPaint.setStrokeWidth(2);
                    mBorderPaint.setTextSize(80);

                    canvas.drawLine(mDstRect.left, mDstRect.top, mDstRect.right, mDstRect.top, mBorderPaint);
                    canvas.drawLine(mDstRect.right, mDstRect.top, mDstRect.right, mDstRect.bottom, mBorderPaint);
                    canvas.drawLine(mDstRect.right, mDstRect.bottom, mDstRect.left, mDstRect.bottom, mBorderPaint);
                    canvas.drawLine(mDstRect.left, mDstRect.bottom, mDstRect.left, mDstRect.top, mBorderPaint);

                    // Draw index on top of tile to make the play easier
                    String numberText = String.valueOf(tile.index + 1);
                    mBorderPaint.getTextBounds(numberText, 0, numberText.length(), mTextRect);
                    canvas.drawText(numberText, mDstRect.exactCenterX() - mTextRect.exactCenterX
                            (), mDstRect.exactCenterY() - mTextRect.exactCenterY(), mBorderPaint);
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean ret = mGestureDetectorCompat.onTouchEvent(event);

        if (!ret) {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                drop();
                ret = true;
            } else {
                ret = super.onTouchEvent(event);
            }
        }

        return ret;
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return false;
    }

    @Override
    public void onShowPress(MotionEvent e) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        freeMove(e.getX(), e.getY());
        return true;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        drag(e1.getX(), e1.getY(), e2.getX(), e2.getY());
        return true;
    }

    @Override
    public void onLongPress(MotionEvent e) {

    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        return false;
    }

    private void init(Context context, AttributeSet attributeSet) {
        setClickable(true);

        // Use GestureDetectorCompat to handle gesture events
        mGestureDetectorCompat = new GestureDetectorCompat(context, this);

        // Initialize attributes
        TypedArray typedArray = getContext().obtainStyledAttributes(attributeSet, R.styleable.SlidingPuzzleView);
        final Drawable drawable = typedArray.getDrawable(R.styleable.SlidingPuzzleView_src);
        if (drawable != null) {
            setImageDrawable(drawable);
        }
        mNumRows = typedArray.getInt(R.styleable
                .SlidingPuzzleView_numRows, MIN_NUM_ROWS);
        mNumColumns = typedArray.getInt(R.styleable
                .SlidingPuzzleView_numColumns, MIN_NUM_COLUMNS);
        typedArray.recycle();

        setupData();
    }

    private void setupData() {
        // Make the last tile as the empty position
        mEmptyTileIndex = mNumRows * mNumColumns - 1;

        // Clear drag event
        mDragEvent = null;

        // Use list to shuffle tiles
        List<Tile> tileList = new ArrayList<>();
        for (int index = 0; index < mNumRows * mNumColumns; index++) {
            Tile tile = new Tile(index);
            tileList.add(tile);
        }
        Collections.shuffle(tileList);

        // Initialise and use array to store tiles
        mTiles = new Tile[mNumRows][mNumColumns];
        for (int i = 0; i < tileList.size(); i++) {
            Tile tile = tileList.get(i);

            int row = i / mNumRows;
            int column = i % mNumColumns;
            mTiles[row][column] = tile;

            if (tile.index == mEmptyTileIndex) {
                mEmptyPosition = new Position(row, column);
            }
        }
    }

    private boolean isSolved() {
        boolean solved = true;

        for (int row = 0; row < mNumRows; row++) {
            for (int column = 0; column < mNumColumns; column++) {
                if (mTiles[row][column].index != row * mNumRows + column) {
                    solved = false;
                    break;
                }
            }
        }

        return solved;
    }

    private void setImageDrawable(@Nullable Drawable drawable) {
        mDrawable = drawable;
    }

    private Bitmap getScaledCroppedSourceBitmap() {
        // Get source mBitmap
        Bitmap sourceBitmap = ((BitmapDrawable) mDrawable).getBitmap();

        // Scale source mBitmap to fill screen
        int scaledBitmapWidth;
        int scaledBitmapHeight;
        float screenRatio = (float) getWidth() / (float) getHeight();
        float sourceBitmapRatio = (float) sourceBitmap.getWidth() / (float) sourceBitmap.getHeight();

        if (screenRatio <= sourceBitmapRatio) {
            // Fill height
            scaledBitmapWidth = (int) (sourceBitmapRatio * getHeight());
            scaledBitmapHeight = getHeight();
        } else {
            // Fill width
            scaledBitmapWidth = getWidth();
            scaledBitmapHeight = (int) (getWidth() / sourceBitmapRatio);
        }
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(sourceBitmap, scaledBitmapWidth, scaledBitmapHeight, false);

        // Crop to fit screen
        return Bitmap.createBitmap(scaledBitmap, (scaledBitmapWidth - getWidth()) / 2, (scaledBitmapHeight - getHeight()) / 2, getWidth(), getHeight());
    }

    private void drag(float startX, float startY, float endX, float endY) {
        Position selectedTilePosition = getPosition(startX, startY);

        if (selectedTilePosition.isHorizontalLeftTo(mEmptyPosition) || selectedTilePosition
                .isHorizontalRightTo(mEmptyPosition)) {
            // Selected tile is at the same row of empty position

            float displacementX = endX - startX;
            if ((selectedTilePosition.isHorizontalLeftTo(mEmptyPosition) && displacementX > 0)
                    || (selectedTilePosition.isHorizontalRightTo(mEmptyPosition) && displacementX < 0)) {
                // Drag horizontally
                mDragEvent = new DragEvent();
                mDragEvent.displacement = displacementX;
                mDragEvent.position = selectedTilePosition;

                // Redraw dragging effect
                invalidate();
            }
        } else if (selectedTilePosition.isVerticalOver(mEmptyPosition) || selectedTilePosition
                .isVerticalUnder(mEmptyPosition)) {
            // Selected tile is at the same column of empty position

            float displacementY = endY - startY;
            if ((selectedTilePosition.isVerticalOver(mEmptyPosition) && displacementY > 0)
                    || (selectedTilePosition.isVerticalUnder(mEmptyPosition) && displacementY < 0)) {
                // Drag vertically
                mDragEvent = new DragEvent();
                mDragEvent.displacement = displacementY;
                mDragEvent.position = selectedTilePosition;

                // Redraw dragging effect
                invalidate();
            }
        }
    }

    private void drop() {
        boolean movableDragEvent = false;

        if (mDragEvent != null) {
            int direction = getDirectionTowardsEmptyPosition(mDragEvent
                    .position);
            if ((direction == LEFT || direction == RIGHT) &&
                    Math.abs(mDragEvent.displacement) > getTileWidthInPixel() / 2) {
                movableDragEvent = true;
            } else if ((direction == UP || direction == DOWN)
                    && Math.abs(mDragEvent.displacement) > getTileHeightInPixel() / 2) {
                movableDragEvent = true;
            }
        }

        if (movableDragEvent) {    // Drag's displacement is big enough to trigger a move
            moveTilesIfPossible(mDragEvent.position);
        }

        mDragEvent = null;

        invalidate();
    }

    private void freeMove(float x, float y) {
        // Get selected tile's positions
        Position position = getPosition(x, y);

        // Try to move tile(s) if possible
        moveTilesIfPossible(position);
    }

    private void moveTilesIfPossible(Position position) {
        boolean moved = false;

        // Get selected tile's positions
        int selectedTileRow = position.row;
        int selectedTileColumn = position.column;

        // Get empty tile positions
        int emptyPositionRow = mEmptyPosition.row;
        int emptyPositionColumn = mEmptyPosition.column;
        Tile blankTile = mTiles[emptyPositionRow][emptyPositionColumn];

        if (position.isHorizontalLeftTo(mEmptyPosition)) {
            // Move right
            System.arraycopy(mTiles[selectedTileRow], selectedTileColumn, mTiles[selectedTileRow], selectedTileColumn + 1, emptyPositionColumn - selectedTileColumn);
            moved = true;
        } else if (position.isHorizontalRightTo(mEmptyPosition)) {
            // Move left
            System.arraycopy(mTiles[selectedTileRow], emptyPositionColumn + 1, mTiles[selectedTileRow], emptyPositionColumn, selectedTileColumn - emptyPositionColumn);
            moved = true;
        } else if (position.isVerticalOver(mEmptyPosition)) {
            // Move down
            for (int row = emptyPositionRow; row > selectedTileRow; row--) {
                mTiles[row][selectedTileColumn] = mTiles[row - 1][selectedTileColumn];
            }
            moved = true;
        } else if (position.isVerticalUnder(mEmptyPosition)) {
            // Move up
            for (int row = emptyPositionRow; row < selectedTileRow; row++) {
                mTiles[row][selectedTileColumn] = mTiles[row + 1][selectedTileColumn];
            }
            moved = true;
        }

        if (moved) {
            // Update blank tile
            mTiles[selectedTileRow][selectedTileColumn] = blankTile;
            mEmptyPosition = position;
            
            if(isSolved()){
                Toast.makeText(getContext(), "Solved", Toast.LENGTH_SHORT).show();
            }

            // Redraw after movement
            invalidate();
        }
    }

    private Position getPosition(float x, float y) {
        return new Position((int) (y / getTileHeightInPixel()), (int) (x /
                getTileWidthInPixel()));
    }

    private int getDrawingHorizontalOffset(int row, int column) {
        int offset = 0;
        Position position = new Position(row, column);

        if (mDragEvent != null) {
            if (position.isSameRow(mEmptyPosition)) {
                // Tile is at the same row of empty position

                if (position.isHorizontalLeftTo(mEmptyPosition)) {
                    // Tile is left to empty position
                    if (position.equals(mDragEvent.position) ||
                            (position.isHorizontalRightTo(mDragEvent.position))) {
                        // Tile is the dragged tile, or between empty position and dragged tile
                        offset = Math.min((int) mDragEvent.displacement, getTileWidthInPixel());
                    }
                } else if (position.isHorizontalRightTo(mEmptyPosition)) {
                    // Tile is right to empty position
                    if (position.equals(mDragEvent.position) ||
                            (position.isHorizontalLeftTo(mDragEvent.position))) {
                        // Tile is the dragged tile, or between empty position and dragged tile
                        offset = Math.max((int) mDragEvent.displacement, -getTileWidthInPixel());
                    }
                }
            }
        }

        return offset;
    }

    private int getDrawingVerticalOffset(int row, int column) {
        int offset = 0;

        Position position = new Position(row, column);

        if (mDragEvent != null) {
            if (position.isSameColumn(mEmptyPosition)) {
                // Tile is at the same column of empty position

                if (position.isVerticalUnder(mEmptyPosition)) {
                    // Tile is under empty position
                    if (position.equals(mDragEvent.position) ||
                            (position.isVerticalOver(mDragEvent.position))) {
                        // Tile is the dragged tile, or between empty position and dragged tile
                        offset = Math.max((int) mDragEvent.displacement, -getTileHeightInPixel());
                    }
                } else if (position.isVerticalOver(mEmptyPosition)) {
                    // Tile is over empty position
                    if (position.equals(mDragEvent.position) ||
                            (position.isVerticalUnder(mDragEvent.position))) {
                        // Tile is the dragged tile, or between empty position and dragged tile
                        offset = Math.min((int) mDragEvent.displacement, getTileHeightInPixel());
                    }
                }
            }
        }

        return offset;
    }

    @Direction
    private int getDirectionTowardsEmptyPosition(Position position) {
        int direction = NONE;

        if (position.isHorizontalLeftTo(mEmptyPosition)) {
            direction = RIGHT;
        } else if (position.isHorizontalRightTo(mEmptyPosition)) {
            direction = LEFT;
        } else if (position.isVerticalOver(mEmptyPosition)) {
            direction = DOWN;
        } else if (position.isVerticalUnder(mEmptyPosition)) {
            direction = UP;
        }

        return direction;
    }

    private int getTileWidthInPixel() {
        return getWidth() / mNumColumns;
    }

    private int getTileHeightInPixel() {
        return getHeight() / mNumRows;
    }

    //region Tile
    private static class Tile {
        private final int index;

        private Tile(int index) {
            this.index = index;
        }
    }
    //endregion

    //region Position
    private static class Position {
        final int row;
        final int column;

        Position(int row, int column) {
            this.row = row;
            this.column = column;
        }

        boolean isSameRow(Position position) {
            return position != null && position.row == this.row;
        }

        boolean isSameColumn(Position position) {
            return position != null && position.column == this.column;
        }

        boolean isVerticalOver(Position position) {
            return position != null && row < position.row && column == position.column;
        }

        boolean isVerticalUnder(Position position) {
            return position != null && row > position.row && column == position.column;
        }

        boolean isHorizontalLeftTo(Position position) {
            return position != null && column < position.column && row == position.row;
        }

        boolean isHorizontalRightTo(Position position) {
            return position != null && column > position.column && row == position.row;
        }

        @Override
        public boolean equals(Object obj) {
            return obj != null && obj instanceof Position && ((Position) obj).row == row && (
                    (Position) obj).column == column;
        }
    }
    //endregion

    //region Direction
    private static final int NONE = 0;
    private static final int UP = 1;
    private static final int RIGHT = 2;
    private static final int DOWN = 3;
    private static final int LEFT = 4;

    @IntDef({
            NONE, UP, RIGHT, DOWN, LEFT
    })
    @Retention(RetentionPolicy.SOURCE)
    private @interface Direction {
    }
    //endregion

    //region DragEvent
    private static class DragEvent {
        Position position;
        float displacement;
    }
    //endregion
}
