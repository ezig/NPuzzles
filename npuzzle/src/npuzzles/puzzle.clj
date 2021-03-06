(ns npuzzles.puzzle
	(:require clojure.core.memoize)
    (:require [taoensso.timbre.profiling :as profiling
       :refer (pspy pspy* profile defnp p p*)])
    (:require [clojure.math.numeric-tower :as math]))
(alias 'memo 'clojure.core.memoize)

; Puzzle record has integer number of rows, columsns
; and an row * column length vector of tiles [0, row * column)
; Puzzle is solved if tiles are in increasing order and zero is in bottom right
(defrecord Puzzle [rows cols tiles])

;PUBLIC FUNCTIONS

(declare random-puzzle)
(declare solvable?)
(defn random-solvable-puzzle
	"Given rows and columns, generates a random puzzle that is solvable"
	[rows cols]
	(loop [puzzle (random-puzzle rows cols)]
		(if (solvable? puzzle)
			puzzle
			(recur (random-puzzle rows cols)))))
(defn gen-puzzle
	"Given one dimension, generates random solvable square puzzles
	 Given two dimensions, generates a random solvable puzzle.
	 Given dimensions and tiles, generates puzzle with given tiles
	  (intended only for testing)"
	 ([rows] (random-solvable-puzzle rows rows))
     ([rows cols] (random-solvable-puzzle rows cols))
     ([rows cols tiles] {:rows rows, :cols cols, :tiles tiles}))

(defn to-string
	"Given a puzzle, returns a string representation"
	[{cols :cols tiles :tiles}]
	(loop [string (str (first tiles))
		   lst (rest tiles)]
		   (if (empty? lst)
		   	   string
		   	   (let [hd (first lst) tl (rest lst)]
				   (if (= (mod (count lst) cols) 0)
				   	   (recur (str string "\n" hd) tl)
				   	   (recur (str string hd) tl))))))

(declare find-tile)

(defn valid-directions-helper
	"Given a puzzle, returns a list of direction keywords of valid moves"
	[{rows :rows cols :cols :as puzzle}] 
		(let [zero-idx (find-tile puzzle 0)
			  zeroRow (quot zero-idx cols)
			  zeroCol (mod zero-idx cols)
			  moves {:up (not (= zeroRow (- rows 1))), :down (not (= zeroRow 0)),
			   :left (not (= zeroCol (- cols 1))) , :right (not (= zeroCol 0))}]
			(vec (keys (into {} (filter #(val %) moves))))))

(def valid-directions (memo/memo valid-directions-helper))

(declare swap)

(defn slide-helper
	"Given a direction, moves puzzle in that direction if it is a valid move"
	[{cols :cols rows :rows tiles :tiles :as puzzle} direction]
	(if (not (nil? (some #{direction} (valid-directions puzzle))))
		(let [emptyP (find-tile puzzle 0)
			newP (case direction
				:up (+ emptyP cols)
				:down (- emptyP cols)
				:left (+ emptyP 1)
				:right (- emptyP 1))]
			{:rows rows :cols cols :tiles (swap tiles emptyP newP)})
		puzzle))

(def slide (memo/memo slide-helper))

(defn solved?
	"Given a puzzle, returns true if it is solved"
	[{rows :rows cols :cols tiles :tiles}]
	(= tiles (concat (range 1 (* rows cols)) (list 0))))

(declare abs)
(declare col-of-tile)
(declare row-of-tile)
(defn- manhattan-distance-helper
	"Given a Puzzle, calculates its fitness using the Manhattan Distance 
	 heuristic function"
	[{cols :cols rows :rows tiles :tiles :as puzzle}]
	(loop [d 0 
		   lst tiles]
	 	(if (empty? lst)
	 	    d
	 	    (let [hd (first lst) tl (rest lst)
 	     		  final_index (mod (- hd 1) (* rows cols))
	 	     	  final_row (quot final_index cols)
	 	     	  final_col (mod final_index cols)
				  column (col-of-tile puzzle hd)
				  row (row-of-tile puzzle hd)]
				(if (= hd 0) 
					(recur d tl)
            		(recur (+ d (+ (abs (- row final_row)) (abs (- column final_col))))
            			tl))))))

;Memoized version of manhattan-distance-helper
(def ^:heuristic manhattan-distance (memo/memo manhattan-distance-helper))

(defn tiles-out-of-helper
	"Given a puzzle, calculates the heuristic function 'tiles out of row and column'.
	This function iterates through the tiles in a given puzzle and checks for the following 3 cases.
	If a tile is either not in the row or column of the corresponding tile in the goal state, 1 is added
	to the heuristic score. If a tile is neither in the row nor the column of the corresponding 
	tile in the goal state then 2 is added to the score. If the tile is in its proper location, 
	then the score remains unchanged."
	[{cols :cols rows :rows tiles :tiles :as puzzle}]
	(loop [d 0 lst tiles]
	 	(if (empty? lst)
	 	    d
	 	    (let [hd (first lst) tl (rest lst)
 	     		  final_index (mod (- hd 1) (* rows cols))
	 	     	  final_row (quot final_index cols)
	 	     	  final_col (mod final_index cols)
				  column (col-of-tile puzzle hd)
				  row (row-of-tile puzzle hd)
				  out_of_row (not= final_row row) 
				  out_of_col (not= final_col column)]
		    (if (= hd 0)
		    	(recur d tl)
			    (if (and out_of_row out_of_col)
			    	(recur (+ d 2) tl)
			    	(if (or out_of_row out_of_col)
			    		(recur (+ d 1) tl)
			    	    (recur d tl))))))))

(def ^:heuristic tiles-out-of (memo/memo tiles-out-of-helper))
		  

(defn- inversions-vector
	"Given a vector, returns the number of inversions"
	[lst]
	(loop [inv 0 lst lst]
		(if (empty? lst)
			inv
			(let [hd (first lst) tl (rest lst)]
				(recur (+ inv (count (filter #(< % hd) tl))) tl)))))

(defn- linear-conflict-helper
	"Given a Puzzle, calculates the amount of linear conflict in the puzzle
	allowing for more accurate fitness when added to Manhattan Distance"
	[{cols :cols rows :rows tiles :tiles :as puzzle}]
	(let [lst tiles
		  goal_index_vec (into [] (map #(mod (- % 1) (* rows cols)) lst))] 
		(loop [d 0 cnt (- rows 1) idx_vec goal_index_vec]
			(if (= -1 cnt)
			d
			(let [row_hd_idx (* cnt cols)
				  row_tl_idx (+ (* cnt cols) cols)
				  current_row (subvec idx_vec row_hd_idx row_tl_idx)
				  c_row_belongs (filter #(and(>= % row_hd_idx) (and (< % row_tl_idx) (not= % 8))) current_row)]
				  (recur (+ d (* (inversions-vector c_row_belongs) 2)) (dec cnt) idx_vec))))))

(defn- linear-conflict-manhattan-helper
	"Uses manhattan-distance in combination with linear-conflict for more accurate heuristic"
	[{cols :cols rows :rows tiles :tiles :as puzzle}]
	(+ (linear-conflict-helper puzzle) (manhattan-distance-helper puzzle)))

(def ^:heuristic linear-conflict (memo/memo linear-conflict-manhattan-helper))

(defn misplaced-tiles-helper
	"Given a puzzle, returns the number of tiles in the puzzle that are not in the same position as the 
	corresponding tiles in the puzzle's goal state."
	[{tiles :tiles}]
	"Calculates the number of tiles that are in the , ignoring the zero tile"
	(loop [tiles tiles indx 1 misplaced 0]
		(if (empty? tiles)
			misplaced
			(let [hd (tiles 0) tl (into [] (rest tiles))]
				(if (or (= hd indx) (= hd 0))
					(recur tl (+ indx 1) misplaced)
					(recur tl (+ indx 1) (+ 1 misplaced)))))))

;Memoized version of misplace-tiles-helper
(def ^:heuristic misplaced-tiles (memo/memo misplaced-tiles-helper))


(declare inversions)
(defn solvable?
	"Returns true if a given puzzle is solvable, false otherwise"
	[{rows :rows cols :cols :as puzzle}]
	(let [is_odd (odd? (* rows cols))]
		(or
	  		(and (odd? cols) (even? (inversions puzzle)))
	  		(and 
	  			(even? cols)
	  			(even? rows)
	  			(odd? (+ (row-of-tile puzzle 0) (inversions puzzle))))
	  		(and 
	  			(even? cols)
	  			(odd? rows)
	  			(even? (+ (row-of-tile puzzle 0) (inversions puzzle)))))))

(defn dir-between
	"Given two puzzles, determines the direction to slide from puzzle1
	to get puzzle2. ASSUMES PUZZLES HAVE SAME DIMENSION"
	[puzzle1 puzzle2]
	(let [diff (- (find-tile puzzle2 0) (find-tile puzzle1 0))]
		(cond 
			(= -1 diff) :right
			(= 1 diff) :left
			(= (:cols puzzle1) diff) :up
			(= (- (:cols puzzle1)) diff) :down
			:else nil)))


(defn check-sol
	"Given a puzzle and a vector of directions representing a solution,
	slides the puzzle by the sequence of directions and returns true if the
	final puzzle is solved, false otherwise"
	[puzzle dirs]
	(loop [puz puzzle
		   dir-list dirs]
		(if (empty? dir-list)
			(solved? puz)
			(recur (slide puz (first dir-list)) (rest dir-list)))))

(defn visualize-sol
	"Given a puzzle and a vector of directions representing a solution,
	slides the puzzle by the sequence of directions and prints the puzzle
	at each step"
	[puzzle dirs]
	(loop [puz puzzle
		   dir-list dirs]
		(println (to-string puz))
		(println "")
		(if (empty? dir-list)
			nil
			(recur (slide puz (first dir-list)) (rest dir-list)))))


;PRIVATE FUNCTIONS
(defn- abs [n] (max n (- n)))

(defn- swap
	"Given a vector and two indices, swaps the values at those indices"
	[v i1 i2]
	(assoc v i1 (v i2), i2 (v i1)))

(defn- find-tile
	"Returns the index of a tile in a puzzle"
	[{tiles :tiles} tile]
	(.indexOf tiles tile))

(defn- row-of-tile
	"Returns the row that a given tile is on"
	[{cols :cols :as puzzle} tile]
	(quot (find-tile puzzle tile) cols))

(defn- col-of-tile
	"Returns the col that a given tile is on"
	[{cols :cols :as puzzle} tile]
	(mod (find-tile puzzle tile) cols))

(defn- inversions
	"Given a puzzle, returns the number of inversions"
	[{tiles :tiles}]
	(loop [inv 0
		   lst (remove #(= % 0) tiles)]
		(if (empty? lst)
			inv
			(let [hd (first lst) tl (rest lst)]
				(recur (+ inv (count (filter #(< % hd) tl))) tl)))))

(defn- random-puzzle
	"Given a number of rows and columns, creates random puzzle
	MAY NOT BE SOLVABLE. Unsolvable puzzles should never be publically available"
	[r c]
	{:rows r, :cols c, :tiles (shuffle (range (* r c)))})